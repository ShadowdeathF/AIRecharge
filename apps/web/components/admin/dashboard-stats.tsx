"use client"
import Link from "next/link"
import { AlertCircle, ArrowRight, CheckCircle2 } from "lucide-react"
import { useLocale } from "@/lib/context"
import { money } from "@/lib/dashboard"
import type { DashboardStats as Stats, LowStockProduct } from "@/types"
export function DashboardStats({ stats }: { stats: Stats }) {
  const { locale } = useLocale(); const zh = locale === "zh"
  return <section className="analytics-summary" aria-label={zh ? "成交数据" : "Sales summary"}>
    {[{ label: zh ? "今日经营" : "Today", amount: stats.today_sales, count: stats.today_orders, prefix: zh ? "今日" : "Today" }, { label: zh ? "本月累计" : "This month", amount: stats.month_sales, count: stats.month_orders, prefix: zh ? "本月" : "Monthly" }].map(group => <div className="analytics-summary-group" key={group.label}>
      <div className="analytics-summary-title"><h2>{group.label}</h2><span>{zh ? "仅统计已付款及已发货订单" : "Paid and delivered orders only"}</span></div>
      <div className="analytics-summary-values"><div><p>{zh ? `${group.prefix}成交额` : `${group.prefix} sales`}</p><strong>{money(group.amount)}</strong></div><div><p>{zh ? `${group.prefix}成交订单` : `${group.prefix} paid orders`}</p><strong>{group.count.toLocaleString()}<small>{zh ? "笔" : "orders"}</small></strong></div></div>
    </div>)}
  </section>
}
export function LowStockAlert({ products }: { products: LowStockProduct[] }) {
  const { locale } = useLocale(); const zh = locale === "zh"
  const groups = [{ label: zh ? "缺货" : "Out of stock", cls: "stock-critical", list: products.filter(p => p.available_stock === 0) }, { label: zh ? "低库存" : "Low stock", cls: "stock-low", list: products.filter(p => p.available_stock > 0) }]
  return <section className="admin-panel stock-attention"><div className="admin-panel-heading"><h2>{zh ? "库存关注" : "Inventory attention"}</h2><Link href="/admin/card-keys">{zh ? "查看全部" : "View all"}<ArrowRight size={13} /></Link></div>
    {products.length === 0 ? <div className="admin-empty-compact"><CheckCircle2 size={24} /><p>{zh ? "库存充足，暂无补货提醒" : "Stock is healthy. No alerts."}</p></div> : groups.filter(group=>group.list.length>0).map(group => <div key={group.cls} className="stock-group"><h3 className={group.cls}><AlertCircle size={12} />{group.label}<span>{group.list.length} {zh ? "项" : "items"}</span></h3>
      {group.list.map(item => <div className="stock-product" key={item.product_id}><div><strong>{item.title}</strong><p>{zh ? "剩余" : "Available"} {item.available_stock}<span>｜</span>{zh ? "阈值" : "Threshold"} {item.threshold}</p></div><Link href={`/admin/card-keys?product_id=${item.product_id}&import=1`}>{zh ? "去补货" : "Restock"}</Link></div>)}
    </div>)}
  </section>
}
export function VisitOverview({ stats }: { stats: Stats }) {
  const { locale } = useLocale(); const zh = locale === "zh"
  return <section className="admin-panel visit-overview"><div className="admin-panel-heading"><h2>{zh ? "今日访客" : "Today's traffic"}</h2></div><dl><div><dt>PV</dt><dd>{stats.today_pv.toLocaleString()}</dd></div><div><dt>UV</dt><dd>{stats.today_uv.toLocaleString()}</dd></div><div><dt>{zh ? "转化率" : "Conversion"}</dt><dd>{stats.conversion_rate.toFixed(2)}%</dd></div></dl><p>{zh ? "成交订单" : "Paid orders"} {stats.today_orders} ÷ {zh ? "访客" : "Visitors"} {stats.today_uv}{stats.today_uv === 0 ? (zh ? "（暂无访问数据）" : " (no visitors)") : ""}</p></section>
}
