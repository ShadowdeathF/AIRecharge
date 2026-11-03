BEGIN;
ALTER TABLE finance_trades ADD COLUMN IF NOT EXISTS reconciliation_cost numeric(18,2) NOT NULL DEFAULT 0;
ALTER TABLE finance_people ADD COLUMN IF NOT EXISTS confirmed boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX IF NOT EXISTS finance_company_external_reference ON finance_trades(company_id,lower(external_reference)) WHERE external_reference IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS finance_expense_actual_reference ON finance_cash_movements(reference) WHERE kind='EXPENSE' AND reference IS NOT NULL AND reference<>'';
CREATE INDEX IF NOT EXISTS finance_card_keys_order ON card_keys(order_id,order_item_id,import_batch_id);
COMMIT;
