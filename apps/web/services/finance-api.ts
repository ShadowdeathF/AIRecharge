import { request } from "./api"
import type { Money } from "@/types/finance"
import type { PaginatedData } from "@/types"
import type { FinanceCompany,CompanyQuote,CompanyMember,PurchaseView,PurchaseInput,PurchaseLot,ReceiveInput,FinancePerson,FinanceFilters,LedgerResult,TradeView,OfflineInput,ImportPreview,FinanceOverview,FinanceTrade,ProfitPreview,Settlement,SettlementView,QuarterPreview,CashInput,CashMovement } from "@/types/finance"
function query(params:object){const qs=new URLSearchParams();for(const [key,value] of Object.entries(params))if(value!==null&&value!==undefined&&value!=="")qs.set(key,String(value));return qs.toString()}
const send=<T>(url:string,body:unknown,method="POST")=>request<T>(url,{method,body:JSON.stringify(body)})
export const companyApi={
  list:()=>request<FinanceCompany[]>("/admin/companies"),
  save:(id:string|null,input:{name:string;enabled:boolean;note:string})=>send<FinanceCompany>(`/admin/companies${id?"/"+id:""}`,input,id?"PUT":"POST"),
  quotes:(id:string)=>request<CompanyQuote[]>(`/admin/companies/${id}/quotes`),
  quote:(id:string,input:{product_id:string;spec_id:string|null;unit_price:string})=>send<CompanyQuote>(`/admin/companies/${id}/quotes`,input),
  members:(id:string)=>request<CompanyMember[]>(`/admin/companies/${id}/members`),
  bind:(id:string,userId:string)=>send<void>(`/admin/companies/${id}/members/${userId}`,{},"PUT"),
  unbind:(userId:string)=>request<void>(`/admin/companies/members/${userId}`,{method:"DELETE"}),
}
export const procurementApi={
  list:(page=1)=>request<PaginatedData<PurchaseView>>(`/admin/procurement/purchases?page=${page}&page_size=20`),
  detail:(id:string)=>request<PurchaseView>(`/admin/procurement/purchases/${id}`),
  create:(body:PurchaseInput)=>send<PurchaseView>("/admin/procurement/purchases",body),
  receive:(id:string,body:ReceiveInput)=>send<PurchaseLot>(`/admin/procurement/purchases/${id}/receipts`,body),
  receiveCards:(id:string,receipt:ReceiveInput,card_keys:string)=>send<PurchaseLot>(`/admin/procurement/purchases/${id}/receipts-with-cards`,{receipt,card_keys}),
  lots:()=>request<PurchaseLot[]>("/admin/procurement/lots"),
  link:(id:string,batch:string)=>send<void>(`/admin/procurement/lots/${id}/card-batch/${batch}`,{}),
}
export const financeApi={
  trades:(filters:FinanceFilters)=>request<LedgerResult>(`/admin/finance/trades?${query(filters)}`),
  trade:(id:string)=>request<TradeView>(`/admin/finance/trades/${id}`),
  onlineOptions:(company_id:string)=>request<{id:string;email:string;paid_at:string;amount:Money}[]>(`/admin/finance/trades/online-options?${query({company_id})}`),
  sync:async(onBatch?:()=>void)=>{let synced=0;for(let page=0;page<200;page++){const batch=await send<{synced:number;has_more:boolean}>("/admin/finance/trades/sync",{});synced+=batch.synced;onBatch?.();if(!batch.has_more)return {synced}}throw new Error(`已同步${synced}笔，请再次点击继续同步`)},
  offline:(body:OfflineInput)=>send<TradeView>("/admin/finance/trades",body),
  receipt:(id:string,body:{amount:string;happened_on:string;final_receipt:boolean;reason:string;idempotency_key:string;version:number})=>send<TradeView>(`/admin/finance/trades/${id}/receipts`,body),
  cost:(id:string,body:{line_key:string;lot_ids:string[];version:number})=>send<TradeView>(`/admin/finance/trades/${id}/costs`,body),
  reconcile:(id:string,body:{income_delta:string;cost_delta:string;reason:string;idempotency_key:string;version:number})=>send<TradeView>(`/admin/finance/trades/${id}/reconcile`,body),
  changeCompany:(id:string,company_id:string,reason:string,version:number)=>send<TradeView>(`/admin/finance/trades/${id}/company`,{company_id,reason,version},"PUT"),
  personalCash:(id:string)=>request<CashMovement[]>(`/admin/finance/people/${id}/cash`),
  reserveReport:(start_date:string,end_date:string)=>request<Record<"opening"|"accrual"|"expenses"|"closing",Money>>(`/admin/finance/reserve-report?${query({start_date,end_date})}`),
  finalIncome:(id:string,reason:string,version:number)=>send<TradeView>(`/admin/finance/trades/${id}/final-income`,{reason,version}),
  confirm:(id:string,version:number)=>send<TradeView>(`/admin/finance/trades/${id}/confirm`,{version}),
  confirmBatch:(input:{id:string;version:number}[])=>send<{count:number}>("/admin/finance/trades/confirm-batch",input),
  referrer:(id:string,referrer_id:string|null,reason:string,version:number)=>send<TradeView>(`/admin/finance/trades/${id}/referrer`,{referrer_id,reason,version},"PUT"),
  importPreview:(data:OfflineInput[])=>send<ImportPreview>("/admin/finance/trades/import/preview",data),
  importCommit:(data:OfflineInput[])=>send<{ids:string[];count:number}>("/admin/finance/trades/import/commit",data),
  overview:()=>request<FinanceOverview>("/admin/finance/overview"),
  people:()=>request<FinancePerson[]>("/admin/finance/people"),
  savePerson:(id:string|null,body:{name:string;base_role:string;enabled:boolean;dividend_weight:string;version?:number})=>send<FinancePerson>(`/admin/finance/people${id?"/"+id:""}`,body,id?"PUT":"POST"),
  candidates:()=>request<FinanceTrade[]>("/admin/finance/candidates"),
  profitPreview:(trade_ids:string[])=>send<ProfitPreview>("/admin/finance/settlements/preview",{trade_ids}),
  settle:(trade_ids:string[],idempotency_key:string,preview_token:string)=>send<SettlementView>("/admin/finance/settlements",{trade_ids,idempotency_key,preview_token}),
  documents:(page=1)=>request<PaginatedData<Settlement>>(`/admin/finance/settlements?page=${page}&page_size=20`),
  document:(id:string)=>request<SettlementView>(`/admin/finance/settlements/${id}`),
  quarter:(key:string)=>request<QuarterPreview>(`/admin/finance/quarters/${key}/preview`),
  settleQuarter:(quarter:string,idempotency_key:string,preview_token:string)=>send<SettlementView>("/admin/finance/quarters",{quarter,idempotency_key,preview_token}),
  adjust:(body:{trade_id:string;income_delta:string;cost_delta:string;referrer_id:string|null;change_referrer:boolean;reason:string;idempotency_key:string})=>send<SettlementView>("/admin/finance/adjustments",body),
  cash:(body:CashInput)=>send<CashMovement>("/admin/finance/cash",body),
  export:async(filters:FinanceFilters)=>{const token=localStorage.getItem("auth_token");const res=await fetch(`${process.env.NEXT_PUBLIC_API_BASE||"/api"}/admin/finance/trades/export?${query(filters)}`,{headers:token?{Authorization:`Bearer ${token}`}:{}});if(!res.ok){const error=await res.json().catch(()=>null);throw new Error(error?.message||"对账单导出失败")}return res.blob()},
  exportDocument:async(id:string)=>{const token=localStorage.getItem("auth_token");const res=await fetch(`${process.env.NEXT_PUBLIC_API_BASE||"/api"}/admin/finance/settlements/${id}/export`,{headers:token?{Authorization:`Bearer ${token}`}:{}});if(!res.ok)throw new Error("结算单导出失败");return res.blob()},
}
