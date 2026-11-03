BEGIN;
CREATE TABLE IF NOT EXISTS finance_companies (
 id uuid PRIMARY KEY, name varchar(120) NOT NULL UNIQUE, enabled boolean NOT NULL DEFAULT true,
 note varchar(500), version bigint NOT NULL DEFAULT 0, created_at timestamp, updated_at timestamp
);
CREATE TABLE IF NOT EXISTS finance_company_quotes (
 id uuid PRIMARY KEY, company_id uuid NOT NULL REFERENCES finance_companies(id),
 product_id uuid NOT NULL REFERENCES products(id), spec_id uuid REFERENCES product_specs(id),
 sku_key varchar(80) NOT NULL, unit_price numeric(18,2) NOT NULL CHECK (unit_price>=0),
 revision bigint NOT NULL, active boolean NOT NULL DEFAULT true, created_by uuid,
 created_at timestamp, updated_at timestamp, UNIQUE(company_id,sku_key,revision)
);
CREATE UNIQUE INDEX IF NOT EXISTS finance_company_quote_current ON finance_company_quotes(company_id,sku_key) WHERE active;
ALTER TABLE users ADD COLUMN IF NOT EXISTS company_id uuid REFERENCES finance_companies(id);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS company_id uuid REFERENCES finance_companies(id);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS company_name varchar(255);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS company_quote_id uuid REFERENCES finance_company_quotes(id);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS price_source varchar(255);
COMMIT;
