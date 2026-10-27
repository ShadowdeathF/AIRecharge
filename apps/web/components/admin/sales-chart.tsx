"use client"
import { useEffect, useState } from "react"
import { LineChart, Line, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts"
import { useLocale } from "@/lib/context"
import { addCalendarDays, businessToday } from "@/lib/dashboard"
import type { SalesTrend } from "@/types"
export function SalesChart({ trends, range, onRangeChange, loading, error }: { trends: SalesTrend[]; range: { start: string; end: string }; onRangeChange: (range: { start: string; end: string }) => void; loading: boolean; error: string }) {
  const { locale } = useLocale(); const zh = locale === "zh"
  const [start, setStart] = useState(range.start), [end, setEnd] = useState(range.end)
  const [validation, setValidation] = useState("")
  const rangeDays = Math.round((Date.parse(range.end) - Date.parse(range.start)) / 86400000) + 1
  const selectedPeriod = range.end === businessToday() && (rangeDays === 7 || rangeDays === 30) ? String(rangeDays) : "custom"
  useEffect(() => { setStart(range.start); setEnd(range.end) }, [range])
  const noSales = trends.every(row=>row.order_count === 0)
  const axis = { tick: { fontSize: 10, fill: "hsl(var(--muted-foreground))" }, axisLine: false, tickLine: false }
  const tip = { background: "hsl(var(--popover))", border: "1px solid hsl(var(--border))", borderRadius: 8, fontSize: 12, color: "hsl(var(--foreground))" }
  return <section className="admin-panel analytics-chart" aria-label={zh ? "成交趋势" : "Sales trends"}>
    <div className="admin-panel-heading"><h2>{zh ? "成交趋势" : "Sales trends"}</h2><select aria-label={zh ? "趋势时段" : "Trend period"} value={selectedPeriod} onChange={e => { const today=businessToday(); onRangeChange({ start: addCalendarDays(today,1-Number(e.target.value)), end: today }); setValidation("") }}><option value="7">{zh ? "最近7天" : "Last 7 days"}</option><option value="30">{zh ? "最近30天" : "Last 30 days"}</option><option value="custom" disabled>{zh ? "自定义日期" : "Custom dates"}</option></select></div>
    <form className="analytics-date-range" onSubmit={e => { e.preventDefault(); if (!start || !end || start > end || end > businessToday() || (Date.parse(end)-Date.parse(start))/86400000 > 365) { setValidation(zh ? "请选择不超过366天且不晚于今天的有效日期范围" : "Choose a valid range up to 366 days, ending no later than today"); return }; setValidation(""); onRangeChange({ start, end }) }}>
      <input type="date" aria-label={zh ? "开始日期" : "Start date"} value={start} max={businessToday()} onChange={e=>setStart(e.target.value)} /><span>—</span><input type="date" aria-label={zh ? "结束日期" : "End date"} value={end} max={businessToday()} onChange={e=>setEnd(e.target.value)} /><button type="submit">{zh ? "应用" : "Apply"}</button>
    </form>
    {(validation || error) && <p className="admin-error" role="alert">{validation || error}</p>}
    {loading ? <div className="admin-chart-loading">{zh ? "正在加载趋势…" : "Loading trends…"}</div> : <>
      {noSales && !error && <p className="analytics-zero-note">{zh ? "所选日期暂无成交，以下显示每日零值。" : "No sales in this range. Daily zero values are shown."}</p>}
      <div className="analytics-plot"><h3>{zh ? "成交额（元）" : "Sales (CNY)"}</h3><div className="analytics-plot-canvas"><ResponsiveContainer width="100%" height="100%"><LineChart data={trends} margin={{ top: 9, right: 12, left: -22, bottom: 0 }}><CartesianGrid vertical={false} stroke="hsl(var(--border))" strokeDasharray="3 3" /><XAxis dataKey="date" {...axis} tickFormatter={value=>value.slice(5).replace("-","/")} minTickGap={22} /><YAxis {...axis} /><Tooltip contentStyle={tip} formatter={value=>[Number(value).toFixed(2),zh ? "成交额（元）" : "Sales (CNY)"]} /><Line type="monotone" dataKey="sales_amount" stroke="hsl(var(--primary))" strokeWidth={2} dot={trends.length<=7 ? { r: 3, fill: "hsl(var(--card))", strokeWidth: 2 } : false} isAnimationActive={false} /></LineChart></ResponsiveContainer></div></div>
      <div className="analytics-plot analytics-orders-plot"><h3>{zh ? "成交订单（笔）" : "Paid orders (count)"}</h3><div className="analytics-plot-canvas"><ResponsiveContainer width="100%" height="100%"><BarChart data={trends} margin={{ top: 8, right: 12, left: -22, bottom: 0 }}><CartesianGrid vertical={false} stroke="hsl(var(--border))" strokeDasharray="3 3" /><XAxis dataKey="date" {...axis} tickFormatter={value=>value.slice(5).replace("-","/")} minTickGap={22} /><YAxis {...axis} allowDecimals={false} /><Tooltip contentStyle={tip} formatter={value=>[value,zh ? "成交订单（笔）" : "Paid orders"]} /><Bar dataKey="order_count" fill="hsl(var(--primary))" maxBarSize={34} radius={[3,3,0,0]} isAnimationActive={false} /></BarChart></ResponsiveContainer></div></div>
    </>}
  </section>
}
