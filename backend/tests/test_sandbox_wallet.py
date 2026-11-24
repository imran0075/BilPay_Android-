import os
from decimal import Decimal
from uuid import uuid4

os.environ.setdefault("DATABASE_URL", "sqlite:///./test_bilpay_sandbox.db")
os.environ.setdefault("BILPAY_JWT_SECRET", "test-only-secret-that-is-long-enough-123456")
os.environ.setdefault("BILPAY_TEST_MODE", "true")

from fastapi.testclient import TestClient
import app.main as api


def register(client, prefix="017"):
    phone = prefix + str(uuid4().int % 100_000_000).zfill(8)
    response = client.post("/api/v1/auth/register", json={
        "phone": phone, "password": "test-password-123", "full_name": "Sandbox User"
    })
    assert response.status_code == 200
    return phone, {"Authorization": f"Bearer {response.json()['access_token']}"}


def test_sandbox_is_disabled_by_default(monkeypatch):
    monkeypatch.delenv("BILPAY_SANDBOX_LEDGER_ENABLED", raising=False)
    with TestClient(api.app) as client:
        status = client.get("/api/v1/sandbox/status")
        _, headers = register(client)
        wallet = client.get("/api/v1/sandbox/wallet", headers=headers)
    assert status.json()["sandbox_enabled"] is False
    assert status.json()["live_wallet_enabled"] is False
    assert wallet.status_code == 503
    assert wallet.json()["detail"]["code"] == "SANDBOX_DISABLED"


def test_free_internal_sandbox_transfer_and_idempotency(monkeypatch):
    monkeypatch.setenv("BILPAY_SANDBOX_LEDGER_ENABLED", "true")
    with TestClient(api.app) as client:
        admin_phone, admin_headers = register(client, "019")
        monkeypatch.setenv("BILPAY_ADMIN_PHONES", admin_phone)
        assert client.get("/api/v1/auth/me", headers=admin_headers).json()["is_admin"] is True
        sender_phone, sender_headers = register(client)
        assert client.get("/api/v1/auth/me", headers=sender_headers).json()["is_admin"] is False
        recipient_phone, recipient_headers = register(client, "018")

        grant = client.post("/api/v1/admin/sandbox/grant", headers=admin_headers,
                            json={"user_phone": sender_phone, "amount": "1000.00"})
        assert grant.status_code == 200
        assert grant.json()["real_money"] is False
        assert grant.json()["redeemable"] is False

        transfer_payload = {"recipient_phone": recipient_phone, "amount": "500.00"}
        headers = {**sender_headers, "Idempotency-Key": "sandbox-transfer-unique-001"}
        sent = client.post("/api/v1/sandbox/transfers/internal", headers=headers, json=transfer_payload)
        replay = client.post("/api/v1/sandbox/transfers/internal", headers=headers, json=transfer_payload)
        sender_wallet = client.get("/api/v1/sandbox/wallet", headers=sender_headers)
        recipient_wallet = client.get("/api/v1/sandbox/wallet", headers=recipient_headers)
        live_wallet = client.get("/api/v1/wallet", headers=sender_headers)
        sender_user_id = client.get("/api/v1/auth/me", headers=sender_headers).json()["id"]
        with api.SessionLocal() as test_db:
            sender_ledger_total = test_db.query(api.SandboxLedgerEntry).filter(
                api.SandboxLedgerEntry.user_id == sender_user_id,
                api.SandboxLedgerEntry.mode == "SANDBOX"
            ).with_entities(api.func.coalesce(api.func.sum(api.SandboxLedgerEntry.amount), 0)).scalar()

    assert sent.status_code == 200
    assert sent.json()["fee"] == "0.00"
    assert sent.json()["recipient_credit"] == "500.00"
    assert sent.json()["mode"] == "SANDBOX"
    assert replay.status_code == 200
    assert replay.json()["transaction_id"] == sent.json()["transaction_id"]
    assert sender_wallet.json()["balance"] == "500.00"
    assert recipient_wallet.json()["balance"] == "500.00"
    assert Decimal(str(sender_ledger_total)).quantize(Decimal("0.01")) == Decimal("500.00")
    assert live_wallet.status_code == 503


def test_sandbox_transfer_rejects_insufficient_funds_and_non_admin_grant(monkeypatch):
    monkeypatch.setenv("BILPAY_SANDBOX_LEDGER_ENABLED", "true")
    monkeypatch.setenv("BILPAY_ADMIN_PHONES", "01600000000")
    with TestClient(api.app) as client:
        sender_phone, sender_headers = register(client)
        recipient_phone, _ = register(client, "018")
        assert client.get("/api/v1/auth/me", headers=sender_headers).json()["is_admin"] is False
        denied = client.post("/api/v1/admin/sandbox/grant", headers=sender_headers,
                             json={"user_phone": sender_phone, "amount": "10.00"})
        transfer = client.post("/api/v1/sandbox/transfers/internal",
                               headers={**sender_headers, "Idempotency-Key":"sandbox-insufficient-001"},
                               json={"recipient_phone":recipient_phone,"amount":"1.00"})
    assert denied.status_code == 403
    assert transfer.status_code == 400
