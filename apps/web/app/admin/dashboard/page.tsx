"use client"
import { useCallback, useEffect, useRef, useState } from "react"
import Link from "next/link"
import { ArrowRight, Box, CalendarDays, ChevronRight, RefreshCw, AlertCircle } from "lucide-react"
import { toast } from "sonner"
import { useLocale } from "@/lib/context"
import { businessToday } from "@/lib/dashboard"
import { periodRange, validRange, type DateRange, type Period } from "@/lib/workbench"
import { adminWorkbenchApi } from "@/services/api"
import { WorkbenchSummary } from "@/components/admin/workbench-summary"
import { WorkbenchChart } from "@/components/admin/workbench-chart"
import { WorkbenchOrders, WorkbenchDay, LedgerState } from "@/components/admin/workbench-orders"
import { WorkbenchOrderDetail } from "@/components/admin/workbench-order-detail"
import type { WorkbenchFilters, WorkbenchOverview, WorkbenchLedger, WorkbenchOrder } from "@/types/workbench"
import "./workbench.css"
export default function AdminDashboardPage() {
  const { locale } = useLocale(), zh = locale === "zh"
  const [range, setRange] = useState<DateRange>(() => periodRange("7")), [draft, setDraft] = useState<DateRange>(() => periodRange("7"))
  const [period, setPeriod] = useState<Period>("7"), [payment, setPayment] = useState(""), [compare, setCompare] = useState(false)
  const [overview, setOverview] = useState<WorkbenchOverview | null>(null), [overviewError, setOverviewError] = useState(""), [overviewLoading, setOverviewLoading] = useState(true)
  const [ledger, setLedger] = useState<WorkbenchLedger | null>(null), [ledgerError, setLedgerError] = useState(""), [ledgerLoading, setLedgerLoading] = useState(true)
  const [status, setStatus] = useState(""), [keyword, setKeyword] = useState(""), [page, setPage] = useState(1), [pageSize, setPageSize] = useState(5), [ledgerDay, setLedgerDay] = useState<string | null>(null)
  const [selectedDate, setSelectedDate] = useState<string | null>(null), [dayPayment, setDayPayment] = useState("")
  const [dayData, setDayData] = useState<WorkbenchLedger | null>(null), [dayError, setDayError] = useState(""), [dayLoading, setDayLoading] = useState(false)
  const [selected, setSelected] = useState(new Map<string, WorkbenchOrder>()), [detail, setDetail] = useState<WorkbenchOrder | null>(null)
  const [refresh, setRefresh] = useState(0), [exporting, setExporting] = useState(false), [rangeError, setRangeError] = useState("")
  const [metric, setMetric] = useState<"sales_amount" | "order_count">("sales_amount")
  const dateInput = useRef<HTMLInputElement>(null)
  const retry = useCallback(() => setRefresh(value => value + 1), [])
  const errorText = (error: unknown) => error instanceof Error ? error.message : (zh ? "数据加载失败，请重试。" : "Unable to load data. Please retry.")
  useEffect(() => {
    let cancelled = false; setOverviewLoading(true); setOverviewError(""); setOverview(null)
    adminWorkbenchApi.overview({ start_date: range.start, end_date: range.end, payment_method: payment || undefined }).then(data => { if (!cancelled) setOverview(data) }).catch(error => { if (!cancelled) { setOverview(null); setOverviewError(errorText(error)) } }).finally(() => { if (!cancelled) setOverviewLoading(false) })
    return () => { cancelled = true }
  }, [range, payment, refresh])
  useEffect(() => {
    let cancelled = false; setLedgerLoading(true); setLedgerError(""); setLedger(null)
    adminWorkbenchApi.orders({ start_date: ledgerDay || range.start, end_date: ledgerDay || range.end, payment_method: payment || undefined, status: status || undefined, keyword: keyword || undefined, page, page_size: pageSize }).then(data => { if (!cancelled) setLedger(data) }).catch(error => { if (!cancelled) { setLedger(null); setLedgerError(errorText(error)) } }).finally(() => { if (!cancelled) setLedgerLoading(false) })
    return () => { cancelled = true }
  }, [range, payment, status, keyword, page, pageSize, ledgerDay, refresh])
  useEffect(() => {
    if (!selectedDate) { setDayData(null); return }
    let cancelled = false; setDayLoading(true); setDayError(""); setDayData(null)
    adminWorkbenchApi.orders({ start_date: selectedDate, end_date: selectedDate, payment_method: payment || dayPayment || undefined, page: 1, page_size: 6 }).then(data => { if (!cancelled) setDayData(data) }).catch(error => { if (!cancelled) setDayError(errorText(error)) }).finally(() => { if (!cancelled) setDayLoading(false) })
    return () => { cancelled = true }
  }, [selectedDate, dayPayment, payment, refresh])
  useEffect(() => { if (ledger && page > Math.max(1, Math.ceil(ledger.pagination.total / pageSize))) setPage(Math.max(1, Math.ceil(ledger.pagination.total / pageSize))) }, [ledger, page, pageSize])
  const resetLedger = () => { setPage(1); setSelected(new Map()) }
  const changeRange = (next: DateRange) => { setRange(next); setSelectedDate(null); setLedgerDay(null); setDayPayment(""); setOverview(null); resetLedger() }
  const choosePeriod = (value: Period) => { setPeriod(value); setRangeError(""); if (value === "custom") { dateInput.current?.focus(); return }; const next = periodRange(value); setDraft(next); changeRange(next) }
  const inspectMetric = (nextStatus: string) => { setStatus(nextStatus); setKeyword(""); setSelectedDate(null); setDayPayment(""); setLedgerDay(null); resetLedger(); document.querySelector<HTMLElement>("#workbench-ledger")?.focus({ preventScroll: true }); document.querySelector("#workbench-ledger")?.scrollIntoView({ behavior: "smooth", block: "start" }) }
  const openOrder = (id: string) => { const order = [...(ledger?.list || []), ...(dayData?.list || [])].find(row => row.id === id); if (order) setDetail(order) }
  const exportCsv = async (scope: "period" | "selection" | "day") => {
    if (exporting || (scope === "selection" && !selected.size) || (scope === "day" && !selectedDate)) return
    const start = scope === "day" ? selectedDate! : ledgerDay || range.start, end = scope === "day" ? selectedDate! : ledgerDay || range.end
    const filters: WorkbenchFilters & { ids?: string } = { start_date: start, end_date: end, payment_method: (scope === "day" ? payment || dayPayment : payment) || undefined }
    if (scope !== "day") { filters.status = status || undefined; filters.keyword = keyword || undefined }
    if (scope === "selection") { if (selected.size > 100) { toast.error(zh ? "单次最多导出100笔勾选订单，更多请使用筛选范围导出。" : "Export up to 100 selected orders, or export the entire filtered ledger."); return }; filters.ids = [...selected.keys()].join(",") }
    setExporting(true)
    try { const blob = await adminWorkbenchApi.exportCsv(filters), url = URL.createObjectURL(blob), anchor = document.createElement("a"); anchor.href = url; anchor.download = `AIRecharge-${start}-${end}${scope === "selection" ? "-selected" : ""}.csv`; document.body.append(anchor); anchor.click(); anchor.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000); toast.success(zh ? "对账单已导出" : "Ledger exported") } catch (error) { toast.error(errorText(error)) } finally { setExporting(false) }
  }
  const toggleOrder = (order: WorkbenchOrder) => { if (!selected.has(order.id) && selected.size >= 100) { toast.error(zh ? "最多勾选100笔，更多请导出筛选范围。" : "Select up to 100 orders, or export the filtered ledger."); return }; setSelected(previous => { const next = new Map(previous); if (next.has(order.id)) next.delete(order.id); else next.set(order.id, order); return next }) }
  const togglePage = () => { if (selected.size + (ledger?.list || []).filter(row => !selected.has(row.id)).length > 100) { toast.error(zh ? "最多勾选100笔，更多请导出筛选范围。" : "Select up to 100 orders, or export the filtered ledger."); return }; setSelected(previous => { const next = new Map(previous), rows = ledger?.list || [], all = rows.every(row => next.has(row.id)); for (const row of rows) { if (all) next.delete(row.id); else next.set(row.id, row) }; return next }) }
  const periods: { key: Period; label: string }[] = [{ key: "today", label: zh ? "今天" : "Today" }, { key: "yesterday", label: zh ? "昨天" : "Yesterday" }, { key: "7", label: zh ? "近7天" : "7 days" }, { key: "30", label: zh ? "近30天" : "30 days" }, { key: "lastMonth", label: zh ? "上月" : "Last month" }, { key: "custom", label: zh ? "自定义" : "Custom" }]
  return <div className={`workbench ${selectedDate ? "has-inspector" : ""}`} data-testid="admin-workbench">
    <div className="wb-main-column"><header className="wb-heading"><div><h1>{zh ? "经营工作台" : "Business workspace"}</h1><p>{zh ? "从汇总到每一笔订单" : "From the overview to every transaction"}</p></div><button type="button" className="wb-icon-button" aria-label={zh ? "刷新工作台" : "Refresh workspace"} title={zh ? "刷新数据" : "Refresh data"} onClick={retry} disabled={overviewLoading || ledgerLoading}><RefreshCw size={17} className={overviewLoading ? "animate-spin" : ""} /></button></header>
      <section className="wb-filters" aria-label={zh ? "全局统计筛选" : "Global report filters"}>
        <div className="wb-periods">{periods.map(item => <button type="button" key={item.key} aria-pressed={period === item.key} onClick={() => choosePeriod(item.key)}>{item.label}</button>)}</div>
        <form className="wb-date-form" onSubmit={event => { event.preventDefault(); if (!validRange(draft)) { setRangeError(zh ? "请选择不晚于今天、最长366天的有效日期范围。" : "Choose a valid range up to 366 days, ending no later than today."); return }; setPeriod("custom"); setRangeError(""); changeRange(draft) }}>
          <CalendarDays size={15} /><input ref={dateInput} aria-label={zh ? "统计开始日期" : "Report start date"} type="date" value={draft.start} max={businessToday()} onChange={event => { setDraft(value => ({ ...value, start: event.target.value })); setPeriod("custom") }} /><ArrowRight size={12} /><input aria-label={zh ? "统计结束日期" : "Report end date"} type="date" value={draft.end} max={businessToday()} onChange={event => { setDraft(value => ({ ...value, end: event.target.value })); setPeriod("custom") }} /><button type="submit">{zh ? "应用" : "Apply"}</button>
        </form>
        <span className="wb-time-basis" title="Asia/Shanghai">{zh ? "按付款时间" : "Paid time"}</span><select aria-label={zh ? "统计支付渠道" : "Report payment channel"} value={payment} onChange={event => { setPayment(event.target.value); setSelectedDate(null); setDayPayment(""); resetLedger() }}><option value="">{zh ? "全部渠道" : "All channels"}</option><option value="alipay">{zh ? "支付宝" : "Alipay"}</option><option value="wechat">{zh ? "微信支付" : "WeChat Pay"}</option></select>
      </section>
      {rangeError && <p className="wb-error wb-range-error" role="alert">{rangeError}</p>}
      {overviewLoading && !overview ? <div className="wb-overview-loading" role="status">{zh ? "正在汇总经营数据…" : "Loading overview…"}</div> : overviewError ? <LedgerState loading={false} error={overviewError} empty={false} retry={retry} /> : overview && <>
        <WorkbenchSummary summary={overview.summary} previous={overview.comparison.summary} compare={compare} onInspect={inspectMetric} />
        <WorkbenchChart trend={overview.trend} selectedDate={selectedDate} onSelect={setSelectedDate} compare={compare} onCompare={setCompare} metric={metric} onMetric={setMetric} start={range.start} end={range.end} />
        <section className="wb-operations" aria-label={zh ? "经营待办" : "Operational actions"}><button type="button" onClick={() => inspectMetric("PAID")}><Box size={19} /><span>{zh ? "本周期待发货" : "Period awaiting delivery"} <b>{overview.summary.undelivered_count}</b> {zh ? "笔" : "orders"}</span><ChevronRight size={15} /></button><Link href="/admin/card-keys"><AlertCircle size={18} /><span>{zh ? "当前低库存" : "Current low stock"} <b>{overview.low_stock_products.length}</b> {zh ? "项" : "items"}</span><span className="wb-text">{zh ? "补货" : "Restock"}<ChevronRight size={14} /></span></Link></section>
      </>}
      <WorkbenchOrders data={ledger} loading={ledgerLoading} error={ledgerError} retry={retry} status={status} onStatus={value => { setStatus(value); resetLedger() }} keyword={keyword} onSearch={value => { setKeyword(value); resetLedger() }} page={page} onPage={setPage} pageSize={pageSize} onPageSize={value => { setPageSize(value); resetLedger() }} selected={selected} onToggle={toggleOrder} onTogglePage={togglePage} onOpen={openOrder} onExport={selection => void exportCsv(selection ? "selection" : "period")} exporting={exporting} day={ledgerDay} onClearDay={() => { setLedgerDay(null); resetLedger() }} />
      <p className="wb-accounting-note"><AlertCircle size={13} />{zh ? "渠道到账及手续费尚未核对。此处为订单实收，导出后可与渠道账单核对。" : "Channel receipts and fees are not reconciled. Export order revenue to compare with your channel statement."}</p>
    </div>
    {selectedDate && <WorkbenchDay date={selectedDate} data={dayData} loading={dayLoading} error={dayError} retry={retry} payment={dayPayment} globalPayment={payment} onPayment={setDayPayment} onClose={() => setSelectedDate(null)} onViewAll={() => { setLedgerDay(selectedDate); setPayment(payment || dayPayment); setDayPayment(""); setStatus(""); setKeyword(""); resetLedger(); setSelectedDate(null); setTimeout(() => document.querySelector<HTMLElement>("#workbench-ledger")?.focus(), 0) }} onExport={() => void exportCsv("day")} onOpen={openOrder} exporting={exporting} />}
    {detail && <WorkbenchOrderDetail key={detail.id} order={detail} onClose={() => setDetail(null)} />}
  </div>
}
