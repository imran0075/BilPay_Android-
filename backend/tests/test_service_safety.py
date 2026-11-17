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
