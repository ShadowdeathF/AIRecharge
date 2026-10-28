"use client"
import { useEffect, useRef, useState } from "react"
import { ChevronLeft, ChevronRight, Download, Search, X, ClipboardList, ArrowUpRight, Loader2 } from "lucide-react"
import { useLocale } from "@/lib/context"
import { money } from "@/lib/dashboard"
import { orderTime, selectedAmount, shortId } from "@/lib/workbench"
import { getPaymentLabel } from "@/components/shared/payment-icon"
import { WorkbenchDialog } from "./workbench-dialog"
import type { WorkbenchLedger, WorkbenchOrder } from "@/types/workbench"

export function WorkbenchStatus({ status }: { status: string }) {
  const { locale } = useLocale(), zh = locale === "zh"
  return <span className={`wb-status ${status === "PAID" ? "is-waiting" : ""}`}>{status === "PAID" ? (zh ? "待发货" : "Awaiting delivery") : (zh ? "已发货" : "Delivered")}</span>
}
export function LedgerState({ loading, error, empty, retry }: { loading: boolean; error: string; empty: boolean; retry: () => void }) {
  const { locale } = useLocale(), zh = locale === "zh"
  if (loading) return <div className="wb-state" role="status"><Loader2 size={23} className="animate-spin" /><p>{zh ? "正在读取订单…" : "Loading orders…"}</p></div>
  if (error) return <div className="wb-state wb-error" role="alert"><p>{error}</p><button type="button" onClick={retry}>{zh ? "重试" : "Retry"}</button></div>
  if (empty) return <div className="wb-state"><ClipboardList size={27} /><p>{zh ? "没有符合条件的成交订单" : "No paid orders match these filters"}</p><small>{zh ? "尝试调整日期、渠道或搜索内容。" : "Try a different date, channel or search."}</small></div>
  return null
}

export function WorkbenchOrders({ data, loading, error, retry, status, onStatus, keyword, onSearch, page, onPage, pageSize, onPageSize, selected, onToggle, onTogglePage, onOpen, onExport, exporting, day, onClearDay }: {
  data: WorkbenchLedger | null; loading: boolean; error: string; retry: () => void; status: string; onStatus: (value: string) => void
  keyword: string; onSearch: (value: string) => void; page: number; onPage: (value: number) => void; pageSize: number; onPageSize: (value: number) => void
  selected: Map<string, WorkbenchOrder>; onToggle: (order: WorkbenchOrder) => void; onTogglePage: () => void; onOpen: (id: string) => void
  onExport: (selection: boolean) => void; exporting: boolean; day: string | null; onClearDay: () => void
}) {
  const { locale, t } = useLocale(), zh = locale === "zh"
  const [search, setSearch] = useState(keyword)
  useEffect(() => setSearch(keyword), [keyword])
  const ref = useRef<HTMLInputElement>(null)
  const rows = data?.list || [], total = data?.pagination.total || 0, pages = Math.max(1, Math.ceil(total / pageSize))
  const checked = rows.filter(row => selected.has(row.id)).length
  useEffect(() => { if (ref.current) ref.current.indeterminate = checked > 0 && checked < rows.length }, [checked, rows.length])
  return <section className="wb-panel wb-ledger" id="workbench-ledger" tabIndex={-1} aria-labelledby="wb-ledger-title">
    <header className="wb-section-heading"><div><h2 id="wb-ledger-title">{zh ? "本周期交易明细" : "Period transactions"}</h2>{day && <button type="button" className="wb-filter-chip" onClick={onClearDay}>{day.slice(5).replace("-", "/")}<X size={12} /><span className="sr-only">{zh ? "清除日期筛选" : "Clear day filter"}</span></button>}</div><button type="button" className="wb-button" onClick={() => onExport(false)} disabled={exporting || loading || !total}><Download size={15} />{zh ? "导出对账单" : "Export ledger"}</button></header>
    <div className="wb-ledger-tools"><form className="wb-search" role="search" onSubmit={event => { event.preventDefault(); onSearch(search.trim()) }}><Search size={15} /><input aria-label={zh ? "搜索订单或支付单号" : "Search order or payment reference"} maxLength={100} value={search} onChange={event => setSearch(event.target.value)} placeholder={zh ? "搜索订单、商品或支付单号…" : "Order, product or payment reference…"} /><button type="submit">{zh ? "搜索" : "Search"}</button>{keyword && <button type="button" aria-label={zh ? "清除搜索" : "Clear search"} onClick={() => { setSearch(""); onSearch("") }}><X size={14} /></button>}</form><select aria-label={zh ? "明细状态" : "Ledger status"} value={status} onChange={event => onStatus(event.target.value)}><option value="">{zh ? "全部成交状态" : "All paid orders"}</option><option value="PAID">{zh ? "已付款待发货" : "Awaiting delivery"}</option><option value="DELIVERED">{zh ? "已发货" : "Delivered"}</option></select></div>
    <LedgerState loading={loading} error={error} empty={!rows.length} retry={retry} />
    {!loading && !error && rows.length > 0 && <div className="wb-table-scroll"><table><thead><tr><th><input ref={ref} type="checkbox" aria-label={zh ? "选择本页订单" : "Select this page"} checked={checked === rows.length} onChange={onTogglePage} /></th>{(zh ? ["付款时间", "订单号 / 商品", "支付渠道", "实收金额", "状态", "操作"] : ["Paid", "Order / product", "Channel", "Amount", "Status", "Action"]).map(label => <th key={label}>{label}</th>)}</tr></thead><tbody>{rows.map(order => <tr key={order.id} className={selected.has(order.id) ? "is-selected" : ""}>
      <td><input type="checkbox" aria-label={`${zh ? "选择订单" : "Select order"} ${order.id}`} checked={selected.has(order.id)} onChange={() => onToggle(order)} /></td><td className="wb-nowrap">{orderTime(order.paid_at, locale)}</td><td><button type="button" className="wb-order-link" title={order.id} onClick={() => onOpen(order.id)}>{shortId(order.id)}</button><span className="wb-product-title" title={order.product_title}>{order.product_title || "—"}</span></td><td>{order.payment_method ? getPaymentLabel(order.payment_method, t) : "—"}</td><td className="wb-amount">{money(order.actual_amount)}</td><td><WorkbenchStatus status={order.status} /></td><td><button type="button" className="wb-text" aria-label={`${zh ? "查看订单" : "Inspect order"} ${order.id}`} onClick={() => onOpen(order.id)}>{zh ? "查看" : "View"}<ArrowUpRight size={13} /></button></td>
    </tr>)}</tbody></table></div>}
    <footer className="wb-ledger-footer"><div className="wb-selection">{selected.size ? <><strong>{zh ? `已选 ${selected.size} 笔` : `${selected.size} selected`} · {money(selectedAmount([...selected.values()]))}</strong><button type="button" className="wb-text" onClick={() => onExport(true)} disabled={exporting}><Download size={14} />{zh ? "导出所选" : "Export selected"}</button></> : <span>{zh ? "勾选合计（最多100笔），或导出筛选范围" : "Select up to 100 orders, or export all matches"}</span>}</div><div className="wb-pagination"><span>{zh ? `共 ${total} 笔` : `${total} orders`}</span><select value={pageSize} aria-label={zh ? "每页条数" : "Page size"} onChange={event => onPageSize(Number(event.target.value))}>{[5, 10, 20, 50].map(size => <option key={size} value={size}>{size}/{zh ? "页" : "page"}</option>)}</select><button type="button" aria-label={zh ? "上一页" : "Previous page"} disabled={loading || page <= 1} onClick={() => onPage(page - 1)}><ChevronLeft size={15} /></button><span>{page} / {pages}</span><button type="button" aria-label={zh ? "下一页" : "Next page"} disabled={loading || page >= pages} onClick={() => onPage(page + 1)}><ChevronRight size={15} /></button></div></footer>
    <div className="wb-ledger-total">{zh ? "当前筛选合计" : "Filtered total"}<strong>{money(data?.totals.sales_amount || 0)}</strong><span>{zh ? "按付款时间 · CNY · Asia/Shanghai" : "Paid time · CNY · Asia/Shanghai"}</span></div>
  </section>
}

export function WorkbenchDay({ date, data, loading, error, retry, payment, globalPayment, onPayment, onClose, onViewAll, onExport, onOpen, exporting }: {
  date: string; data: WorkbenchLedger | null; loading: boolean; error: string; retry: () => void; payment: string; globalPayment: string
  onPayment: (value: string) => void; onClose: () => void; onViewAll: () => void; onExport: () => void; onOpen: (id: string) => void; exporting: boolean
}) {
  const { locale, t } = useLocale(), zh = locale === "zh"
  const [mobile, setMobile] = useState(false)
  useEffect(() => { const query = matchMedia("(max-width: 1199px)"); const update = () => setMobile(query.matches); update(); query.addEventListener("change", update); return () => query.removeEventListener("change", update) }, [])
  const title = zh ? `${date.slice(5, 7)}月${date.slice(8)}日订单` : `Orders · ${date.slice(5).replace("-", "/")}`
  const panel = <aside className="wb-day" aria-label={title}><header><h2>{title}</h2><button type="button" aria-label={zh ? "关闭当天订单" : "Close day orders"} onClick={onClose}><X size={19} /></button></header>
    <p className="wb-day-summary">{loading ? "—" : data?.pagination.total || 0} {zh ? "笔 · 实收" : "orders · revenue"} <strong>{loading ? "—" : money(data?.totals.sales_amount || 0)}</strong></p>
    <div className="wb-day-filters"><select aria-label={zh ? "当天订单渠道" : "Day order channel"} disabled={!!globalPayment} value={globalPayment || payment} onChange={event => onPayment(event.target.value)}><option value="">{zh ? "全部渠道" : "All channels"}</option><option value="alipay">{zh ? "支付宝" : "Alipay"}</option><option value="wechat">{zh ? "微信支付" : "WeChat Pay"}</option></select><button type="button" className="wb-text" onClick={() => onPayment("")} disabled={!!globalPayment || !payment}>{zh ? "清除筛选" : "Clear"}</button></div>
    <LedgerState loading={loading} error={error} empty={!data?.list.length} retry={retry} />
    {!loading && !error && <div className="wb-day-orders">{data?.list.map(order => <button type="button" className="wb-day-order" key={order.id} onClick={() => onOpen(order.id)} aria-label={`${zh ? "查看订单" : "Inspect order"} ${order.id}`}><span><b>{shortId(order.id)}</b><small>{orderTime(order.paid_at, locale)}</small><em>{order.payment_method ? getPaymentLabel(order.payment_method, t) : "—"}</em></span><span><span className="wb-day-product" title={order.product_title}>{order.product_title || "—"}</span><strong>{money(order.actual_amount)}</strong><WorkbenchStatus status={order.status} /></span></button>)}</div>}
    <footer><button type="button" className="wb-button is-primary" onClick={onViewAll} disabled={loading || !!error || !data?.pagination.total}>{zh ? `查看全部 ${data?.pagination.total || 0} 笔` : `View all ${data?.pagination.total || 0}`}<ChevronRight size={15} /></button><button type="button" className="wb-button" onClick={onExport} disabled={exporting || loading || !data?.pagination.total}><Download size={14} />{zh ? "导出当日" : "Export day"}</button></footer>
  </aside>
  return mobile ? <WorkbenchDialog label={title} drawer onClose={onClose}>{panel}</WorkbenchDialog> : panel
}
