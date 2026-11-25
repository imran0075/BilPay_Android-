-- BilPay Internal Wallet Engine
-- Apply only to the isolated bilpay-internal-wallet Neon branch.
-- New wallet accounts start at zero; this migration creates no balances or ledger entries.

CREATE TABLE IF NOT EXISTS public.internal_wallet_accounts (
  id SERIAL PRIMARY KEY,
  wallet_id VARCHAR(32) NOT NULL UNIQUE,
  user_id INTEGER NOT NULL UNIQUE REFERENCES public.users(id) ON DELETE RESTRICT,
  currency VARCHAR(3) NOT NULL DEFAULT 'BDT' CHECK (currency = 'BDT'),
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','FROZEN','CLOSED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.internal_wallet_transfers (
  id SERIAL PRIMARY KEY,
  transaction_id VARCHAR(40) NOT NULL UNIQUE,
  idempotency_key VARCHAR(100) NOT NULL UNIQUE,
  sender_wallet_id VARCHAR(32) NOT NULL REFERENCES public.internal_wallet_accounts(wallet_id),
  recipient_wallet_id VARCHAR(32) NOT NULL REFERENCES public.internal_wallet_accounts(wallet_id),
  amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
  fee NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (fee = 0),
  status VARCHAR(20) NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED','REVERSED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CHECK (sender_wallet_id <> recipient_wallet_id)
);

CREATE TABLE IF NOT EXISTS public.internal_wallet_ledger_entries (
  id SERIAL PRIMARY KEY,
  entry_id VARCHAR(40) NOT NULL UNIQUE,
  transaction_id VARCHAR(40) NOT NULL REFERENCES public.internal_wallet_transfers(transaction_id),
  wallet_id VARCHAR(32) NOT NULL REFERENCES public.internal_wallet_accounts(wallet_id),
  entry_type VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT','CREDIT')),
  amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
  currency VARCHAR(3) NOT NULL DEFAULT 'BDT' CHECK (currency = 'BDT'),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (transaction_id, wallet_id, entry_type)
);

CREATE INDEX IF NOT EXISTS ix_internal_wallet_ledger_wallet_created
  ON public.internal_wallet_ledger_entries(wallet_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_internal_wallet_transfers_sender_created
  ON public.internal_wallet_transfers(sender_wallet_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_internal_wallet_transfers_recipient_created
  ON public.internal_wallet_transfers(recipient_wallet_id, created_at DESC);
