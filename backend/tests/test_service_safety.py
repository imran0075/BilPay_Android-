import os
from uuid import uuid4

os.environ.setdefault("DATABASE_URL", "sqlite:///./test_bilpay_service_safety.db")
os.environ.setdefault("BILPAY_JWT_SECRET", "test-only-secret-that-is-long-enough-123456")
os.environ.setdefault("BILPAY_TEST_MODE", "true")

from fastapi.testclient import TestClient
import app.main as api


def register(client):
    phone = "018" + str(uuid4().int % 100_000_000).zfill(8)
    response = client.post(
        "/api/v1/auth/register",
        json={"phone": phone, "password": "test-password-123", "full_name": "Safety Test"},
    )
    assert response.status_code == 200
    return response.json()["access_token"]


def test_public_status_never_advertises_live_transactions():
    with TestClient(api.app) as client:
        response = client.get("/api/v1/status")

    assert response.status_code == 200
    payload = response.json()
    assert payload["live_transactions_enabled"] is False
    assert payload["database_enabled"] is True


def test_service_catalogue_and_providers_remain_disabled_until_integrated():
    with TestClient(api.app) as client:
        services = client.get("/api/v1/services")
        providers = client.get("/api/v1/providers")

    assert services.status_code == 200
    assert services.json()["items"]
    assert all(item["enabled"] is False for item in services.json()["items"])

    assert providers.status_code == 200
    assert providers.json()["live_transactions_enabled"] is False
    assert all(
        item["status"] == "awaiting_merchant_api"
        for item in providers.json()["items"]
    )


def test_demo_simulation_is_rejected_when_test_mode_is_disabled(monkeypatch):
    monkeypatch.setattr(api, "TEST_MODE_ENABLED", False)

    with TestClient(api.app) as client:
        token = register(client)
        response = client.post(
            "/api/v1/test-payments/simulate",
            headers={"Authorization": f"Bearer {token}"},
            json={
                "service_id": "electricity",
                "provider_id": "bkash",
                "amount": "100.00",
                "outcome": "success",
                "currency": "BDT",
            },
        )
        history = client.get(
            "/api/v1/transactions",
            headers={"Authorization": f"Bearer {token}"},
        )

    assert response.status_code == 404
    assert history.status_code == 200
    assert history.json()["items"] == []



def test_admin_can_only_change_catalogue_visibility(monkeypatch):
    phone = "017" + str(uuid4().int % 100_000_000).zfill(8)
    monkeypatch.setenv("BILPAY_ADMIN_PHONES", phone)
    with TestClient(api.app) as client:
        registered = client.post(
            "/api/v1/auth/register",
            json={"phone": phone, "password": "test-password-123", "full_name": "Admin Safety"},
        )
        assert registered.status_code == 200
        headers = {"Authorization": f"Bearer {registered.json()['access_token']}"}

        before = client.get("/api/v1/admin/services", headers=headers)
        assert before.status_code == 200
        assert before.json()["live_transactions_enabled"] is False

        changed = client.put(
            "/api/v1/admin/services/mobile_recharge",
            headers=headers,
            json={"visible": False},
        )
        assert changed.status_code == 200
        assert changed.json()["visible"] is False
        assert changed.json()["enabled"] is False
        assert changed.json()["live_transactions_enabled"] is False

        public = client.get("/api/v1/services")
        mobile = next(x for x in public.json()["items"] if x["id"] == "mobile_recharge")
        assert mobile["visible"] is False
        assert mobile["enabled"] is False
        assert public.json()["live_transactions_enabled"] is False

        payment = client.post(
            "/api/v1/payments/initiate",
            headers=headers,
            json={
                "service_id": "mobile_recharge",
                "provider_id": "bkash",
                "amount": "50.00",
                "customer_reference": "01700000000",
                "customer_phone": phone,
            },
        )
        assert payment.status_code == 503
        assert "কোনো টাকা কাটা হয়নি" in payment.json()["detail"]["message"]


def test_non_admin_cannot_change_service_visibility(monkeypatch):
    monkeypatch.setenv("BILPAY_ADMIN_PHONES", "01900000000")
    with TestClient(api.app) as client:
        token = register(client)
        response = client.put(
            "/api/v1/admin/services/electricity",
            headers={"Authorization": f"Bearer {token}"},
            json={"visible": False},
        )
    assert response.status_code == 403
