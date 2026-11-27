import os
from uuid import uuid4

os.environ.setdefault("DATABASE_URL", "sqlite:///./test_bilpay_internal_wallet.db")
os.environ.setdefault("BILPAY_JWT_SECRET", "test-only-secret-that-is-long-enough-123456")
os.environ.setdefault("BILPAY_TEST_MODE", "true")

from fastapi.testclient import TestClient
import app.main as api


def register(client, prefix="017"):
    phone = prefix + str(uuid4().int % 100_000_000).zfill(8)
    response = client.post("/api/v1/auth/register", json={
        "phone": phone,
        "password": "test-password-123",
        "full_name": "Internal Wallet User"
    })
    assert response.status_code == 200
    return phone, {"Authorization": f"Bearer {response.json()['access_token']}"}


def test_internal_wallet_is_created_with_zero_balance_and_no_external_funding(monkeypatch):
    monkeypatch.delenv("BILPAY_INTERNAL_WALLET_TRANSFERS_ENABLED", raising=False)
    with TestClient(api.app) as client:
        _, headers = register(client)
        status = client.get("/api/v1/internal-wallet/status")
        wallet = client.get("/api/v1/internal-wallet", headers=headers)
        history = client.get("/api/v1/internal-wallet/transactions", headers=headers)

    assert status.status_code == 200
    assert status.json()["wallet_accounts_enabled"] is True
    assert status.json()["internal_transfers_enabled"] is False
    assert status.json()["external_funding_enabled"] is False
    assert status.json()["withdrawals_enabled"] is False
    assert wallet.status_code == 200
    assert wallet.json()["wallet_id"].startswith("BPW-")
    assert wallet.json()["balance"] == "0.00"
    assert wallet.json()["real_money"] is False
    assert wallet.json()["redeemable"] is False
    assert wallet.json()["funding_enabled"] is False
    assert history.status_code == 200
    assert history.json()["items"] == []


def test_internal_transfer_is_blocked_until_flag_is_enabled(monkeypatch):
    monkeypatch.delenv("BILPAY_INTERNAL_WALLET_TRANSFERS_ENABLED", raising=False)
    with TestClient(api.app) as client:
        _, sender_headers = register(client)
        recipient_phone, _ = register(client, "018")
        response = client.post(
            "/api/v1/internal-wallet/transfers",
            headers={**sender_headers, "Idempotency-Key": "internal-wallet-disabled-001"},
            json={"recipient_phone": recipient_phone, "amount": "1.00"}
        )
    assert response.status_code == 503


def test_internal_transfer_rejects_zero_balance_without_creating_ledger_entries(monkeypatch):
    monkeypatch.setenv("BILPAY_INTERNAL_WALLET_TRANSFERS_ENABLED", "true")
    with TestClient(api.app) as client:
        _, sender_headers = register(client)
        recipient_phone, recipient_headers = register(client, "018")
        sender = client.get("/api/v1/internal-wallet", headers=sender_headers).json()
        recipient = client.get("/api/v1/internal-wallet", headers=recipient_headers).json()
        response = client.post(
            "/api/v1/internal-wallet/transfers",
            headers={**sender_headers, "Idempotency-Key": "internal-wallet-no-funds-001"},
            json={"recipient_phone": recipient_phone, "amount": "1.00"}
        )
        sender_after = client.get("/api/v1/internal-wallet", headers=sender_headers).json()
        recipient_after = client.get("/api/v1/internal-wallet", headers=recipient_headers).json()
        history = client.get("/api/v1/internal-wallet/transactions", headers=sender_headers).json()

    assert sender["wallet_id"] != recipient["wallet_id"]
    assert response.status_code == 400
    assert sender_after["balance"] == "0.00"
    assert recipient_after["balance"] == "0.00"
    assert history["items"] == []



def test_internal_transfer_posts_balanced_ledger_and_is_idempotent(monkeypatch):
    monkeypatch.setenv("BILPAY_INTERNAL_WALLET_TRANSFERS_ENABLED", "true")
    with TestClient(api.app) as client:
        _, sender_headers = register(client)
        recipient_phone, recipient_headers = register(client, "018")
        sender_wallet = client.get("/api/v1/internal-wallet", headers=sender_headers).json()
        recipient_wallet = client.get("/api/v1/internal-wallet", headers=recipient_headers).json()

        # Seed a clearly test-only opening credit directly into the isolated test database.
        with api.SessionLocal() as test_db:
            test_db.add(api.InternalWalletLedgerEntry(
                entry_id=uuid4().hex,
                transaction_id="TEST-CREDIT-" + uuid4().hex,
                wallet_id=sender_wallet["wallet_id"],
                entry_type="CREDIT",
                amount="100.00",
                currency="BDT"
            ))
            test_db.commit()

        headers = {**sender_headers, "Idempotency-Key": "internal-wallet-transfer-unique-001"}
        payload = {"recipient_phone": recipient_phone, "amount": "25.00"}
        sent = client.post("/api/v1/internal-wallet/transfers", headers=headers, json=payload)
        replay = client.post("/api/v1/internal-wallet/transfers", headers=headers, json=payload)
        sender_after = client.get("/api/v1/internal-wallet", headers=sender_headers).json()
        recipient_after = client.get("/api/v1/internal-wallet", headers=recipient_headers).json()
        history = client.get("/api/v1/internal-wallet/transactions", headers=sender_headers).json()

    assert sender_wallet["wallet_id"] != recipient_wallet["wallet_id"]
    assert sent.status_code == 200
    assert sent.json()["mode"] == "INTERNAL_ONLY"
    assert sent.json()["real_money"] is False
    assert sent.json()["fee"] == "0.00"
    assert replay.status_code == 200
    assert replay.json()["transaction_id"] == sent.json()["transaction_id"]
    assert sender_after["balance"] == "75.00"
    assert recipient_after["balance"] == "25.00"
    assert len(history["items"]) == 1



def test_admin_manual_deposit_is_disabled_by_default(monkeypatch):
    monkeypatch.setenv("BILPAY_ADMIN_PHONES", "01700000000")
    monkeypatch.delenv("BILPAY_ADMIN_DEPOSITS_ENABLED", raising=False)
    with TestClient(api.app) as client:
        _, admin_headers = register(client, "017")
        target_phone, _ = register(client, "018")
        admin_phone = client.get("/api/v1/auth/me", headers=admin_headers).json()["phone"]
        monkeypatch.setenv("BILPAY_ADMIN_PHONES", admin_phone)
        response = client.post("/api/v1/admin/internal-wallet/deposits",
            headers={**admin_headers, "Idempotency-Key": "manual-deposit-disabled-001"},
            json={"user_phone": target_phone, "amount": "50.00", "payment_method": "cash", "reference": "CASH-RCPT-001"})
    assert response.status_code == 503


def test_admin_manual_deposit_credits_internal_only_and_is_idempotent(monkeypatch):
    monkeypatch.setenv("BILPAY_ADMIN_DEPOSITS_ENABLED", "true")
    with TestClient(api.app) as client:
        _, admin_headers = register(client, "017")
        target_phone, target_headers = register(client, "018")
        admin_phone = client.get("/api/v1/auth/me", headers=admin_headers).json()["phone"]
        monkeypatch.setenv("BILPAY_ADMIN_PHONES", admin_phone)
        payload = {"user_phone": target_phone, "amount": "125.50", "payment_method": "bank",
                   "reference": "BANK-TXN-REF-9001", "notes": "ব্যাংক জমা যাচাই করা হয়েছে"}
        headers = {**admin_headers, "Idempotency-Key": "manual-deposit-bank-unique-001"}
        posted = client.post("/api/v1/admin/internal-wallet/deposits", headers=headers, json=payload)
        replay = client.post("/api/v1/admin/internal-wallet/deposits", headers=headers, json=payload)
        wallet = client.get("/api/v1/internal-wallet", headers=target_headers).json()
        deposits = client.get("/api/v1/admin/internal-wallet/deposits", headers=admin_headers).json()
        audit = client.get("/api/v1/admin/audit", headers=admin_headers).json()

    assert posted.status_code == 200
    assert posted.json()["status"] == "POSTED"
    assert posted.json()["real_money"] is False
    assert replay.status_code == 200
    assert replay.json()["transaction_id"] == posted.json()["transaction_id"]
    assert wallet["balance"] == "125.50"
    assert wallet["mode"] == "INTERNAL_ONLY"
    assert wallet["real_money"] is False
    assert len(deposits["items"]) == 1
    assert deposits["items"][0]["reference"] == "BANK-TXN-REF-9001"
    assert any(x["action"] == "manual_wallet_deposit" for x in audit["items"])


def test_non_admin_cannot_post_manual_wallet_deposit(monkeypatch):
    monkeypatch.setenv("BILPAY_ADMIN_DEPOSITS_ENABLED", "true")
    monkeypatch.setenv("BILPAY_ADMIN_PHONES", "01912345678")
    with TestClient(api.app) as client:
        _, user_headers = register(client, "017")
        target_phone, _ = register(client, "018")
        response = client.post("/api/v1/admin/internal-wallet/deposits",
            headers={**user_headers, "Idempotency-Key": "manual-deposit-forbidden-001"},
            json={"user_phone": target_phone, "amount": "10.00", "payment_method": "cash", "reference": "CASH-RCPT-002"})
    assert response.status_code == 403


def test_unapproved_user_cannot_submit_agent_cash_in(monkeypatch):
    monkeypatch.setenv("BILPAY_AGENT_CASHIN_ENABLED", "true")
    with TestClient(api.app) as client:
        _, headers = register(client, "017")
        response = client.post(
            "/api/v1/agent/cash-ins",
            headers={**headers, "Idempotency-Key": "agent-cashin-unapproved-001"},
            json={"cash_received": True, "customer_phone": "01812345678", "amount": "50.00"}
        )
    assert response.status_code == 403


def test_admin_approved_agent_cash_in_requires_admin_approval_and_posts_once(monkeypatch):
    monkeypatch.setenv("BILPAY_AGENT_CASHIN_ENABLED", "true")
    monkeypatch.setenv("BILPAY_ADMIN_DEPOSITS_ENABLED", "true")
    with TestClient(api.app) as client:
        _, admin_headers = register(client, "017")
        agent_phone, agent_headers = register(client, "018")
        customer_phone, customer_headers = register(client, "019")
        admin_phone = client.get("/api/v1/auth/me", headers=admin_headers).json()["phone"]
        monkeypatch.setenv("BILPAY_ADMIN_PHONES", admin_phone)

        approved = client.post("/api/v1/admin/agents", headers=admin_headers, json={
            "user_phone": agent_phone, "max_per_tx": "2000.00", "daily_limit": "5000.00"
        })
        assert approved.status_code == 200
        assert approved.json()["status"] == "ACTIVE"

        request = client.post("/api/v1/agent/cash-ins",
            headers={**agent_headers, "Idempotency-Key": "agent-cashin-approved-001"},
            json={"cash_received": True, "customer_phone": customer_phone,
                  "amount": "350.00", "reference": "AGENT-RECEIPT-1"})
        assert request.status_code == 200
        assert request.json()["status"] == "PENDING"
        assert request.json()["real_money"] is False

        before = client.get("/api/v1/internal-wallet", headers=customer_headers).json()
        assert before["balance"] == "0.00"

        admin_queue = client.get("/api/v1/admin/agent-cash-ins?status=PENDING", headers=admin_headers)
        assert admin_queue.status_code == 200
        request_id = next(x["id"] for x in admin_queue.json()["items"]
                          if x["request_id"] == request.json()["request_id"])

        posted = client.post(f"/api/v1/admin/agent-cash-ins/{request_id}/approve",
                             headers=admin_headers)
        replay = client.post(f"/api/v1/admin/agent-cash-ins/{request_id}/approve",
                             headers=admin_headers)
        after = client.get("/api/v1/internal-wallet", headers=customer_headers).json()
        history = client.get("/api/v1/agent/cash-ins", headers=agent_headers).json()

    assert posted.status_code == 200
    assert posted.json()["status"] == "APPROVED"
    assert posted.json()["real_money"] is False
    assert replay.status_code == 200
    assert replay.json()["deposit_transaction_id"] == posted.json()["deposit_transaction_id"]
    assert after["balance"] == "350.00"
    assert after["mode"] == "INTERNAL_ONLY"
    assert len([x for x in history["items"] if x["status"] == "APPROVED"]) == 1
