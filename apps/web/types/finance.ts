export type Money = string
export interface FinanceCompany { id:string;name:string;enabled:boolean;note?:string;version:number }
export interface CompanyQuote { id:string;company_id:string;product_id:string;spec_id?:string;unit_price:Money;active:boolean;revision:number;created_at:string }
export interface CompanyMember { id:string;username:string;email:string;company_id?:string }
export interface Purchase { id:string;supplier:string;purchased_on:string;kind:"MONTHLY"|"SUPPLEMENT";status:"OPEN"|"PARTIAL"|"RECEIVED";note?:string;created_at:string;version:number }
export interface PurchaseLine { id:string;purchase_id:string;product_id:string;spec_id?:string;product_title:string;spec_name?:string;quantity:number;received_quantity:number }
export interface PurchaseLot { id:string;purchase_id:string;purchase_line_id:string;product_id:string;spec_id?:string;quantity:number;total_cost:Money;issued_quantity:number;issued_cost:Money;card_import_batch_id?:string;received_on:string }
export interface PurchaseView { purchase:Purchase;lines:PurchaseLine[];lots:PurchaseLot[] }
export interface PurchaseInput { supplier:string;purchased_on:string;kind:string;lines:{product_id:string;spec_id:string|null;quantity:number}[];idempotency_key:string;note:string }
export interface ReceiveInput { line_id:string;quantity:number;total_cost:string;idempotency_key:string;card_import_batch_id:string|null;received_on:string }
export interface FinancePerson { id:string;name:string;confirmed:boolean;base_role:"TECH"|"BOSS"|"FINANCE"|"OTHER";enabled:boolean;dividend_weight:string;version:number }
export interface StockAllocation { id:string;trade_id:string;line_key:string;lot_id:string;quantity:number;cost:Money }
export interface ItemSnapshot { key:string;product_id:string;spec_id?:string;product_title:string;spec_name?:string;quantity:number;unit_price:Money;cost?:Money;quote_id?:string }
export interface OfflineInput { source_order_id?:string;company_id:string;external_reference:string;business_date:string;items:{product_id:string;spec_id:string|null;quantity:number;unit_price:string;price_reason:string}[];referrer_id:string|null;note:string;idempotency_key:string }
export interface FinanceTrade { id:string;source_order_id?:string;source:"RETAIL_ONLINE"|"COMPANY_ONLINE"|"COMPANY_OFFLINE";company_id?:string;company_name?:string;customer_name?:string;external_reference?:string;business_date:string;due_amount:Money;received_amount:Money;cost_amount?:Money;reconciliation_cost?:Money;receipt_final:boolean;referrer_id?:string;status:"DRAFT"|"CONFIRMED"|"SETTLED";confirmed_at?:string;settlement_id?:string;version:number;items_json:string;note?:string }
export interface TradeRow extends Omit<FinanceTrade,"items_json"> {items:ItemSnapshot[];has_adjustments:boolean;original_received_amount:Money;original_cost_amount?:Money}
export interface FinanceAudit { id:string;action:string;created_at:string;before_data?:string;after_data?:string;reason?:string }
export interface CashMovement {id:string;trade_id?:string;person_id?:string;kind:string;amount:Money;happened_on:string;reference?:string;reason?:string;reverses_id?:string;settlement_id?:string}
export interface FinanceAdjustment {id:string;trade_id:string;origin_id:string;income_delta:Money;cost_delta:Money;change_referrer:boolean;referrer_id?:string;reason:string;created_at:string}
export interface TradeView {trade:FinanceTrade;items:ItemSnapshot[];allocations:StockAllocation[];cash:CashMovement[];history:FinanceAudit[];adjustments:FinanceAdjustment[];effective_received:Money;effective_cost?:Money;effective_referrer_id?:string}
export interface FinanceFilters {start_date:string;end_date:string;company_id?:string;source?:string;status?:string;keyword?:string;page?:number;page_size?:number}
export interface LedgerResult {list:TradeRow[];pagination:{page:number;page_size:number;total:number};totals:{received:Money;confirmed_cost:Money;confirmed_profit:Money;receivable:Money;missing_cost_count:number}}
export interface Distribution {people:Record<string,Money>;precise_people:Record<string,Money>;profit:Money;operating_base:Money;rounding_adjustment:Money;operating:Money;dividend:Money}
export interface ProfitPreview {trades:FinanceTrade[];distribution:Distribution;roles:Record<string,string>;names:Record<string,string>;preview_token:string}
export interface Settlement {id:string;kind:string;origin_id?:string;quarter_key?:string;accounting_date:string;total_amount:Money;operating_base:Money;rounding_adjustment:Money;operating_amount:Money;dividend_amount:Money;snapshot_json:string;created_at:string}
export interface Posting {id:string;settlement_id:string;origin_id:string;person_id?:string;account:string;amount:Money;accounting_date:string}
export interface SettlementView {document:Settlement;postings:Posting[];adjustments:Settlement[]}
export interface QuarterPreview {quarter:string;amount:Money;people:Record<string,Money>;weights:{person_id:string;weight:string}[];names:Record<string,string>;profit_groups:string[];closed:boolean;already_confirmed:boolean;can_confirm:boolean;preview_token:string}
export interface FinanceOverview {operating_balance:Money;dividend_balance:Money;people:{person:FinancePerson;balance:Money}[];candidate_count:number;expenses:CashMovement[];expense_reversals:CashMovement[]}
export interface CashInput {kind:string;person_id:string|null;settlement_id:string|null;amount:string;happened_on:string;reference:string;reason:string;idempotency_key:string;reverses_id:string|null}
export interface ImportPreview {rows:{row:number;valid:boolean;duplicate?:boolean;amount?:Money;error?:string}[];error_count:number;can_commit:boolean}
