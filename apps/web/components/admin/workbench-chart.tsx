"use client"
import { useEffect, useState } from "react"
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ReferenceLine } from "recharts"
import { MousePointer2, ArrowUpRight } from "lucide-react"
import { useLocale } from "@/lib/context"
import { money } from "@/lib/dashboard"
import type { WorkbenchTrend } from "@/types/workbench"

export function WorkbenchChart({ trend, selectedDate, onSelect, compare, onCompare, start, end, metric, onMetric }: {
  trend: WorkbenchTrend[]; selectedDate: string | null; onSelect: (date: string) => void
  compare: boolean; onCompare: (value: boolean) => void; start: string; end: string
  metric: "sales_amount" | "order_count"; onMetric: (value: "sales_amount" | "order_count") => void
}) {
  const { locale } = useLocale(), zh = locale === "zh"
  const [day, setDay] = useState("")
  useEffect(() => setDay(""), [start, end])
  const amount = metric === "sales_amount"
  return <section className="wb-panel wb-chart" aria-label={zh ? "成交趋势" : "Sales trends"}>
    <header className="wb-section-heading"><div><h2>{zh ? "成交趋势" : "Sales trends"}</h2><div className="wb-segment" aria-label={zh ? "趋势指标" : "Chart metric"}>
      <button type="button" aria-pressed={amount} onClick={() => onMetric("sales_amount")}>{zh ? "金额" : "Amount"}</button>
      <button type="button" aria-pressed={!amount} onClick={() => onMetric("order_count")}>{zh ? "订单" : "Orders"}</button>
    </div></div><label className="wb-compare"><input type="checkbox" checked={compare} onChange={event => onCompare(event.target.checked)} />{zh ? "对比上一周期" : "Previous period"}</label></header>
    <div className="wb-chart-legend"><span>{amount ? (zh ? "金额（元）" : "Amount (CNY)") : (zh ? "成交订单（笔）" : "Paid orders")}</span><div><i />{zh ? "本周期" : "Current"}{compare && <><i className="is-previous" />{zh ? "上一周期" : "Previous"}</>}</div></div>
    <div className="wb-chart-canvas"><ResponsiveContainer width="100%" height="100%"><LineChart data={trend} margin={{ top: 16, right: 20, left: -15, bottom: 6 }} onClick={state => { if (state?.activeLabel && trend.some(row => row.date === state.activeLabel)) onSelect(String(state.activeLabel)) }}>
      <CartesianGrid vertical={false} stroke="hsl(var(--border))" strokeDasharray="3 4" />
      <XAxis dataKey="date" axisLine={false} tickLine={false} minTickGap={26} tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickFormatter={value => String(value).slice(5).replace("-", "/")} />
      <YAxis axisLine={false} tickLine={false} allowDecimals={amount} tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} width={62} />
      <Tooltip cursor={{ stroke: "hsl(var(--muted-foreground))", strokeDasharray: "3 3" }} content={({ active, payload, label }) => active && payload?.length ? <div className="wb-tooltip"><small>{String(label).replaceAll("-", "/")}</small>{payload.filter(row => row.value !== undefined).map(row => <p key={row.dataKey}><span>{row.dataKey === metric ? (zh ? "本周期" : "Current") : (zh ? "上一周期" : "Previous")}</span><strong>{amount ? money(Number(row.value)) : `${row.value} ${zh ? "笔" : "orders"}`}</strong></p>)}<em>{zh ? "点击查看当天订单" : "Click to inspect orders"}</em></div> : null} />
      {compare && <Line dataKey={amount ? "previous_sales_amount" : "previous_order_count"} stroke="hsl(var(--muted-foreground))" strokeWidth={1.5} strokeDasharray="5 5" dot={false} isAnimationActive={false} />}
      <Line type="monotone" dataKey={metric} stroke="hsl(var(--primary))" strokeWidth={2.3} dot={trend.length <= 31 ? { r: 3.5, fill: "hsl(var(--card))", strokeWidth: 2 } : false} activeDot={{ r: 5, strokeWidth: 3, fill: "hsl(var(--primary))", stroke: "hsl(var(--card))" }} isAnimationActive={false} />
      {selectedDate && <ReferenceLine x={selectedDate} stroke="hsl(var(--muted-foreground))" strokeDasharray="3 3" />}
    </LineChart></ResponsiveContainer></div>
    <footer className="wb-chart-footer"><span><MousePointer2 size={13} />{zh ? "点击日期追查当天订单" : "Click a day to inspect its orders"}</span><form onSubmit={event => { event.preventDefault(); const value = day || selectedDate || end; if (value >= start && value <= end) onSelect(value) }}><input type="date" aria-label={zh ? "查看订单日期" : "Inspect order date"} min={start} max={end} value={day || selectedDate || end} onChange={event => setDay(event.target.value)} /><button type="submit">{zh ? "查看订单" : "Inspect"}<ArrowUpRight size={13} /></button></form></footer>
  </section>
}
