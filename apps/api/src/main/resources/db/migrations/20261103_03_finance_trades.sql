BEGIN;
CREATE TABLE IF NOT EXISTS finance_control (id varchar(20) PRIMARY KEY,version bigint NOT NULL DEFAULT 0);
INSERT INTO finance_control(id,version) VALUES('MAIN',0) ON CONFLICT(id) DO NOTHING;
CREATE TABLE IF NOT EXISTS finance_people (
 id uuid PRIMARY KEY,name varchar(80) NOT NULL,base_role varchar(20),enabled boolean NOT NULL DEFAULT true,
 dividend_weight numeric(18,6) NOT NULL DEFAULT 0 CHECK(dividend_weight>=0),version bigint NOT NULL DEFAULT 0,
 created_at timestamp,updated_at timestamp
);
CREATE UNIQUE INDEX IF NOT EXISTS finance_people_active_role ON finance_people(base_role) WHERE enabled AND base_role IN ('TECH','BOSS','FINANCE');
CREATE TABLE IF NOT EXISTS finance_trades (
 id uuid PRIMARY KEY,source_order_id uuid UNIQUE REFERENCES orders(id),source_key varchar(255) NOT NULL UNIQUE,
 idempotency_key varchar(128) NOT NULL UNIQUE,creation_fingerprint varchar(64),company_id uuid REFERENCES finance_companies(id),
 company_name varchar(255),customer_name varchar(255),source varchar(20) NOT NULL,external_reference varchar(120),
 business_date date,due_amount numeric(18,2) NOT NULL DEFAULT 0,received_amount numeric(18,2) NOT NULL DEFAULT 0,
 receipt_final boolean NOT NULL DEFAULT false,cost_amount numeric(18,2),items_json text NOT NULL,
 referrer_id uuid REFERENCES finance_people(id),note varchar(500),status varchar(20) NOT NULL DEFAULT 'DRAFT',
 confirmed_at timestamp,settlement_id uuid,created_by uuid,version bigint NOT NULL DEFAULT 0,
 created_at timestamp,updated_at timestamp,CHECK(due_amount>=0 AND received_amount>=0 AND (cost_amount IS NULL OR cost_amount>=0))
);
CREATE INDEX IF NOT EXISTS finance_trades_dates ON finance_trades(business_date,company_id);
CREATE INDEX IF NOT EXISTS finance_trades_pending ON finance_trades(status,confirmed_at);
CREATE TABLE IF NOT EXISTS finance_cash_movements (
 id uuid PRIMARY KEY,trade_id uuid REFERENCES finance_trades(id),person_id uuid REFERENCES finance_people(id),
 kind varchar(30) NOT NULL,amount numeric(18,2) NOT NULL,happened_on date,idempotency_key varchar(128) NOT NULL UNIQUE,
 reference varchar(128),reason varchar(500),reverses_id uuid,created_by uuid,created_at timestamp,updated_at timestamp
);
CREATE INDEX IF NOT EXISTS finance_cash_trade ON finance_cash_movements(trade_id,created_at);
CREATE INDEX IF NOT EXISTS finance_cash_person ON finance_cash_movements(person_id,created_at);
COMMIT;
