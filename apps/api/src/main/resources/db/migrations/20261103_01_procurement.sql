-- Run with psql -v ON_ERROR_STOP=1 before starting this release. No legacy costs are guessed.
BEGIN;
CREATE TABLE IF NOT EXISTS finance_purchase_orders (
 id uuid PRIMARY KEY, supplier varchar(120) NOT NULL, purchased_on date, kind varchar(20) NOT NULL,
 status varchar(20) NOT NULL, idempotency_key varchar(128) NOT NULL UNIQUE, note varchar(500),
 created_by uuid, version bigint NOT NULL DEFAULT 0, created_at timestamp, updated_at timestamp,
 CHECK (kind IN ('MONTHLY','SUPPLEMENT')), CHECK (status IN ('OPEN','PARTIAL','RECEIVED'))
);
CREATE TABLE IF NOT EXISTS finance_purchase_lines (
 id uuid PRIMARY KEY, purchase_id uuid NOT NULL REFERENCES finance_purchase_orders(id),
 product_id uuid NOT NULL REFERENCES products(id), spec_id uuid REFERENCES product_specs(id),
 product_title varchar(255) NOT NULL, spec_name varchar(255), quantity integer NOT NULL,
 received_quantity integer NOT NULL DEFAULT 0, version bigint NOT NULL DEFAULT 0,
 created_at timestamp, updated_at timestamp,
 CHECK (quantity BETWEEN 1 AND 999999), CHECK (received_quantity BETWEEN 0 AND quantity)
);
CREATE TABLE IF NOT EXISTS finance_purchase_lots (
 id uuid PRIMARY KEY, purchase_id uuid NOT NULL REFERENCES finance_purchase_orders(id),
 purchase_line_id uuid NOT NULL REFERENCES finance_purchase_lines(id), product_id uuid NOT NULL REFERENCES products(id),
 spec_id uuid REFERENCES product_specs(id), quantity integer NOT NULL, total_cost numeric(18,2) NOT NULL,
 issued_quantity integer NOT NULL DEFAULT 0, issued_cost numeric(18,2) NOT NULL DEFAULT 0,
 card_import_batch_id uuid UNIQUE REFERENCES card_import_batches(id), received_on date,
 idempotency_key varchar(128) NOT NULL UNIQUE, created_by uuid, version bigint NOT NULL DEFAULT 0,
 created_at timestamp, updated_at timestamp,
 CHECK (quantity BETWEEN 1 AND 999999), CHECK (issued_quantity BETWEEN 0 AND quantity),
 CHECK (total_cost>=0), CHECK (issued_cost BETWEEN 0 AND total_cost)
);
ALTER TABLE card_import_batches ADD COLUMN IF NOT EXISTS procurement_lot_id uuid REFERENCES finance_purchase_lots(id);
CREATE UNIQUE INDEX IF NOT EXISTS card_import_batches_procurement_unique ON card_import_batches(procurement_lot_id) WHERE procurement_lot_id IS NOT NULL;
CREATE TABLE IF NOT EXISTS finance_stock_allocations (
 id uuid PRIMARY KEY, trade_id uuid NOT NULL, line_key varchar(80) NOT NULL,
 lot_id uuid NOT NULL REFERENCES finance_purchase_lots(id), quantity integer NOT NULL, cost numeric(18,2) NOT NULL,
 created_by uuid, created_at timestamp, updated_at timestamp,
 UNIQUE(trade_id,line_key,lot_id), CHECK (quantity>0), CHECK (cost>=0)
);
CREATE INDEX IF NOT EXISTS finance_purchase_lots_sku_fifo ON finance_purchase_lots(product_id,spec_id,received_on,id);
CREATE TABLE IF NOT EXISTS finance_audits (
 id uuid PRIMARY KEY, action varchar(80) NOT NULL, target_id uuid, actor_id uuid,
 before_data text, after_data text, reason varchar(500), created_at timestamp, updated_at timestamp
);
CREATE INDEX IF NOT EXISTS finance_audits_target ON finance_audits(target_id,created_at);
COMMIT;
