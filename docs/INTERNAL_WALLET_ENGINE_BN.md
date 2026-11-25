# BilPay Internal Wallet Engine

## Current scope
- Own wallet ID for every registered BilPay user.
- Initial available balance is exactly BDT 0.00.
- Balance is calculated from immutable debit/credit ledger entries.
- BilPay-to-BilPay internal transfer endpoint with row locks and an idempotency key.
- Zero transfer fee.
- Transaction history for the signed-in wallet.
- No bank API, PSP, payment gateway, cash-in, cash-out, admin credit, or external settlement.

## Safety defaults
- `BILPAY_INTERNAL_WALLET_TRANSFERS_ENABLED=false` by default.
- Wallet account creation and balance/history reads are available.
- Transfer requests remain disabled unless the feature flag is explicitly enabled.
- Even when enabled, a transfer is rejected unless the sender has a sufficient ledger-backed balance.
- No test-credit or manual balance-grant endpoint exists in this module.
- This internal ledger is not a bank deposit, e-money balance, redeemable value, or claim on cash.
- Existing sandbox tables and their data are not reused or modified by this module.

## Endpoints
- `GET /api/v1/internal-wallet/status`
- `GET /api/v1/internal-wallet`
- `POST /api/v1/internal-wallet/transfers` (requires `Idempotency-Key`)
- `GET /api/v1/internal-wallet/transactions`

## Deployment notes
Keep `BILPAY_INTERNAL_WALLET_TRANSFERS_ENABLED=false` in Render until the code, schema, and test suite have been verified. Do not enable real-money functions through this feature flag. The existing sandbox ledger remains separate and disabled.
