import os

os.environ.setdefault("DATABASE_URL", "sqlite:///./test_bilpay_security.db")
os.environ.setdefault("BILPAY_JWT_SECRET", "test-only-secret-that-is-long-enough-123456")
os.environ.setdefault("BILPAY_TEST_MODE", "true")

from fastapi.testclient import TestClient
from app.main import app


def test_client():
    return TestClient(app)


def register(client, phone):
    response = client.post(
        "/api/v1/auth/register",
        json={"phone": phone, "password": "test-password-123", "full_name": "Test User"},
    )
    assert response.status_code == 200
    return response.json()["access_token"]


def test_payment_initiation_requires_authentication():
    with test_client() as client:
        response = client.post(
            "/api/v1/payments/initiate",
            json={
                "service_id": "electricity",
                "provider_id": "bkash",
                "amount": "100.00",
                "customer_reference": "BILL-123",
                "customer_phone": "01712345678",
            },
        )
    assert response.status_code == 401


def test_authenticated_payment_intent_still_blocks_unconfigured_provider():
    with test_client() as client:
        token = register(client, "01700000001")
        response = client.post(
            "/api/v1/payments/initiate",
            headers={"Authorization": f"Bearer {token}"},
            json={
                "service_id": "electricity",
                "provider_id": "bkash",
                "amount": "100.00",
                "customer_reference": "BILL-123",
                "customer_phone": "01712345678",
            },
        )
    assert response.status_code == 503
    assert response.json()["detail"]["code"] == "PROVIDER_API_NOT_CONFIGURED"


def test_admin_endpoints_deny_non_admin_user():
    with test_client() as client:
        token = register(client, "01700000002")
        response = client.get(
            "/api/v1/admin/overview",
            headers={"Authorization": f"Bearer {token}"},
        )
    assert response.status_code == 403


def test_admin_endpoint_allows_allowlisted_user_and_records_audit():
    with test_client() as client:
        token = register(client, "01700000003")
        os.environ["BILPAY_ADMIN_PHONES"] = "01700000003"
        response = client.get(
            "/api/v1/admin/overview",
            headers={"Authorization": f"Bearer {token}"},
        )
        assert response.status_code == 200
        assert response.json()["live_transactions_enabled"] is False
        audit = client.get(
            "/api/v1/admin/audit",
            headers={"Authorization": f"Bearer {token}"},
        )
    assert audit.status_code == 200
    assert any(item["action"] == "view_overview" for item in audit.json()["items"])
