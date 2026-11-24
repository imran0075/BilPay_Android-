import os

os.environ.setdefault("DATABASE_URL", "sqlite:///./test_bilpay_readiness.db")
os.environ.setdefault("BILPAY_JWT_SECRET", "test-only-secret-that-is-long-enough-123456")
os.environ.setdefault("BILPAY_TEST_MODE", "true")

from fastapi.testclient import TestClient
import app.main as api


def test_readiness_reports_integrations_pending_and_live_money_off(monkeypatch):
    monkeypatch.setenv("BILPAY_SANDBOX_LEDGER_ENABLED", "true")
    # Even a mistakenly enabled legacy ledger flag must not advertise live payments.
    monkeypatch.setenv("BILPAY_LIVE_LEDGER_ENABLED", "true")

    with TestClient(api.app) as client:
        response = client.get("/api/v1/readiness")

    assert response.status_code == 200
    payload = response.json()
    assert payload["sandbox_enabled"] is True
    assert payload["live_ledger_enabled"] is True
    assert payload["live_transactions_enabled"] is False
    assert payload["cash_in_enabled"] is False
    assert payload["cash_out_enabled"] is False
    assert payload["external_fund_transfer_enabled"] is False
    assert payload["mobile_recharge_enabled"] is False
    assert payload["bill_payment_enabled"] is False
    assert payload["real_money"] is False
    assert payload["services"]
    assert all(item["ready_for_live"] is False for item in payload["services"])
    assert all(item["credentials_configured"] is False for item in payload["providers"])
