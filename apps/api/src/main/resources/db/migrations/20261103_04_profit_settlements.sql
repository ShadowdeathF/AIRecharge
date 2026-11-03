BEGIN;
CREATE TABLE IF NOT EXISTS finance_settlements (
 id uuid PRIMARY KEY,kind varchar(30) NOT NULL,origin_id uuid REFERENCES finance_settlements(id),quarter_key varchar(12) UNIQUE,
 accounting_date date,total_amount numeric(18,2) NOT NULL DEFAULT 0,operating_base numeric(18,2) NOT NULL DEFAULT 0,
 rounding_adjustment numeric(18,2) NOT NULL DEFAULT 0,operating_amount numeric(18,2) NOT NULL DEFAULT 0,dividend_amount numeric(18,2) NOT NULL DEFAULT 0,
 snapshot_json text NOT NULL,idempotency_key varchar(128) NOT NULL UNIQUE,created_by uuid,created_at timestamp,updated_at timestamp
);
CREATE TABLE IF NOT EXISTS finance_postings (
 id uuid PRIMARY KEY,settlement_id uuid NOT NULL REFERENCES finance_settlements(id),origin_id uuid NOT NULL REFERENCES finance_settlements(id),
 person_id uuid REFERENCES finance_people(id),account varchar(30) NOT NULL,amount numeric(18,2) NOT NULL,
 accounting_date date,posting_key varchar(160) NOT NULL UNIQUE,created_at timestamp,updated_at timestamp,
 CHECK(account IN ('PERSONAL_PAYABLE','OPERATING_RESERVE','DIVIDEND_RESERVE')),
 CHECK((account='PERSONAL_PAYABLE')=(person_id IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS finance_postings_origin ON finance_postings(origin_id,account,person_id);
CREATE INDEX IF NOT EXISTS finance_postings_balance ON finance_postings(account,person_id);
CREATE TABLE IF NOT EXISTS finance_adjustments (
 id uuid PRIMARY KEY,trade_id uuid NOT NULL REFERENCES finance_trades(id),origin_id uuid NOT NULL REFERENCES finance_settlements(id),
 sequence_number bigint NOT NULL,income_delta numeric(18,2) NOT NULL DEFAULT 0,cost_delta numeric(18,2) NOT NULL DEFAULT 0,
 change_referrer boolean NOT NULL DEFAULT false,referrer_id uuid REFERENCES finance_people(id),reason varchar(500) NOT NULL,
 idempotency_key varchar(128) NOT NULL UNIQUE,created_by uuid,created_at timestamp,updated_at timestamp,UNIQUE(origin_id,sequence_number)
);
ALTER TABLE finance_cash_movements ADD COLUMN IF NOT EXISTS settlement_id uuid REFERENCES finance_settlements(id);
CREATE UNIQUE INDEX IF NOT EXISTS finance_cash_reversal_once ON finance_cash_movements(reverses_id) WHERE reverses_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS finance_cash_actual_reference ON finance_cash_movements(kind,person_id,reference) WHERE kind IN ('PAYOUT','RECOVERY') AND reference IS NOT NULL AND reference<>'';
CREATE INDEX IF NOT EXISTS finance_adjustments_trade ON finance_adjustments(trade_id,sequence_number);
COMMIT;
