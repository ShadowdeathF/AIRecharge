"use client"
import { useState } from "react"
import Link from "next/link"
import { Plus,Package,ArrowUpRight,RefreshCw } from "lucide-react"
import { procurementApi } from "@/services/finance-api"
import { loadFinanceProducts } from "@/services/finance-data"
import { cents,decimal } from "@/lib/finance"
import { FinanceBadge,FinanceButton,FinanceMetric,FinanceMoney,FinancePager,FinanceState,FinanceTabs,useFinanceData } from "@/components/admin/finance/ui"
import { PurchaseCreate,PurchaseReceive } from "@/components/admin/finance/purchase-forms"
import type { PurchaseView } from "@/types/finance"
const statuses={OPEN:"待入库",PARTIAL:"部分入库",RECEIVED:"已入库"}
export default function ProcurementPage(){
  const [tab,setTab]=useState("purchases"),[page,setPage]=useState(1),[create,setCreate]=useState(false),[receive,setReceive]=useState<PurchaseView|null>(null)
  const resource=useFinanceData(()=>Promise.all([procurementApi.list(page),procurementApi.lots(),loadFinanceProducts()]),[page])
  const [purchases,lots,products]=resource.data||[null,[],[]]
  const totalCost=lots.reduce((sum,l)=>sum+cents(l.total_cost),BigInt(0)),issued=lots.reduce((sum,l)=>sum+cents(l.issued_cost),BigInt(0))
  return <main className="finance-page"><header className="fn-heading"><div><h1>采购与成本</h1><p>月度采购、缺货补采和单份采购统一管理。成本跟随实际入库与出库批次，已归集的订单成本保留历史记录。</p></div><div className="fn-actions"><FinanceButton onClick={resource.refresh}><RefreshCw size={15}/>刷新</FinanceButton><FinanceButton primary onClick={()=>setCreate(true)} disabled={!products.length}><Plus size={15}/>新建采购单</FinanceButton></div></header>
    <section className="fn-kpis"><FinanceMetric label="采购入库总成本" value={<FinanceMoney value={decimal(totalCost)}/>} hint={`${lots.length} 个实际入库批次`}/><FinanceMetric label="已归集订单成本" value={<FinanceMoney value={decimal(issued)}/>} hint="已记录出库的成本合计"/><FinanceMetric label="未归集采购成本" value={<FinanceMoney value={decimal(totalCost-issued)}/>} hint="包括未出库及待关联订单的采购成本"/><FinanceMetric label="采购单" value={purchases?.pagination.total||0} hint="支持一单分多次入库"/></section>
    <FinanceTabs value={tab} onChange={setTab} tabs={[{key:"purchases",label:"采购单"},{key:"lots",label:"采购成本批次"}]}/>
    <section className="fn-panel"><header className="fn-panel-head"><div><h2>{tab==="purchases"?"采购计划与入库进度":"实际入库与成本归集"}</h2><p>{tab==="purchases"?"整批采购或临时补一份，都按同一采购流程登记。":"每次出库保留来源批次。商城可售卡密数量可在卡密库存中查看。"}</p></div><Link className="fn-button" href="/admin/reconciliation">查看对账<ArrowUpRight size={14}/></Link></header>
    <FinanceState loading={resource.loading} error={resource.error} retry={resource.refresh} empty={!resource.loading&&!resource.error&&(tab==="purchases"?!purchases?.list.length:!lots.length)}/>
    {!resource.loading&&!resource.error&&tab==="purchases"&&!!purchases?.list.length&&<><div className="fn-table-scroll"><table className="fn-table"><thead><tr><th>供应商 / 采购单</th><th>采购日期</th><th>类型</th><th>商品明细</th><th>进度</th><th>操作</th></tr></thead><tbody>{purchases.list.map(view=><tr key={view.purchase.id}><td><strong>{view.purchase.supplier}</strong><small title={view.purchase.id}>{view.purchase.id.slice(0,8)}</small></td><td>{view.purchase.purchased_on}</td><td>{view.purchase.kind==="MONTHLY"?"月度批量":"缺货补采"}</td><td>{view.lines.map(line=><div key={line.id}>{line.product_title}{line.spec_name?` / ${line.spec_name}`:""}<small className="fn-meta"> · {line.received_quantity} / {line.quantity} 份已入库</small></div>)}</td><td><FinanceBadge tone={view.purchase.status==="RECEIVED"?"good":"warn"}>{statuses[view.purchase.status]}</FinanceBadge></td><td><FinanceButton onClick={()=>setReceive(view)} disabled={view.purchase.status==="RECEIVED"}>登记入库</FinanceButton></td></tr>)}</tbody></table></div><FinancePager page={page} total={purchases.pagination.total} onPage={setPage}/></>}
    {!resource.loading&&!resource.error&&tab==="lots"&&!!lots.length&&<div className="fn-table-scroll"><table className="fn-table"><thead><tr><th>入库日期 / 批次</th><th>商品</th><th>入库数量</th><th>已归集数量</th><th>入库总成本</th><th>已归集成本</th><th>来源</th></tr></thead><tbody>{lots.map(lot=>{const p=products.find(p=>p.id===lot.product_id);return <tr key={lot.id}><td>{lot.received_on}<small title={lot.id}>{lot.id.slice(0,8)}</small></td><td>{p?.title||lot.product_id.slice(0,8)}<small>{p?.specs?.find(s=>s.id===lot.spec_id)?.name||"默认规格"}</small></td><td>{lot.quantity} 份</td><td>{lot.issued_quantity} 份</td><td><FinanceMoney value={lot.total_cost}/></td><td><FinanceMoney value={lot.issued_cost}/></td><td><FinanceBadge>{lot.card_import_batch_id?"关联商城卡密":"数量库存"}</FinanceBadge></td></tr>})}</tbody></table></div>}
    </section><p className="fn-note"><Package size={17}/><span>缺少采购来源的历史卡密保留“待补采购成本”，不会被当作零成本计入利润。可在入库时关联原卡密批次，再到对账页同步平台订单。</span></p>
    {create&&<PurchaseCreate products={products} onClose={()=>setCreate(false)} onDone={resource.refresh}/>} {receive&&<PurchaseReceive purchase={receive} onClose={()=>setReceive(null)} onDone={resource.refresh}/>}</main>
}
