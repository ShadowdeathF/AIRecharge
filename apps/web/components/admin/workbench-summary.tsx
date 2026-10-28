"use client"
import { ArrowDown, ArrowUp, ChevronRight } from "lucide-react"
import { useLocale } from "@/lib/context"
import { money } from "@/lib/dashboard"
import type { WorkbenchSummary as Summary } from "@/types/workbench"

export function WorkbenchSummary({ summary, previous, compare, onInspect }: { summary: Summary; previous: Summary; compare: boolean; onInspect: (status: string) => void }) {
  const { locale } = useLocale(), zh = locale === "zh"
  const metrics = [
    { key: "sales_amount" as const, label: zh ? "订单实收" : "Order revenue", money: true, status: "" },
    { key: "order_count" as const, label: zh ? "成交订单" : "Paid orders", money: false, status: "" },
    { key: "average_order" as const, label: zh ? "客单价" : "Average order", money: true, status: "" },
    { key: "undelivered_count" as const, label: zh ? "待发货" : "Awaiting delivery", money: false, status: "PAID" },
  ]
  return <section className="wb-summary" aria-label={zh ? "本周期成交数据" : "Period summary"}>{metrics.map(metric => {
    const value = Number(summary[metric.key]), before = Number(previous[metric.key])
    const percentage = before > 0 ? (value - before) / before * 100 : null
    return <button type="button" className="wb-metric" key={metric.key} onClick={() => onInspect(metric.status)} aria-label={`${zh ? "查看" : "Inspect"}${metric.label}`}>
      <span>{metric.label}</span><div><strong>{metric.money ? money(value) : value.toLocaleString()}</strong>{!metric.money && <small>{zh ? "笔" : "orders"}</small>}<ChevronRight size={15} /></div>
      {compare ? <p className={metric.key === "undelivered_count" && value > before ? "is-warning" : ""}>{percentage !== null ? <><span className="wb-delta">{percentage >= 0 ? <ArrowUp size={13} /> : <ArrowDown size={13} />}{Math.abs(percentage).toFixed(1)}%</span><span>{zh ? "较上周期" : "vs prior"} {metric.money ? money(before) : before.toLocaleString()}</span></> : <span>{zh ? "上周期" : "Previous"} {metric.money ? money(before) : before.toLocaleString()}{zh ? "，暂无可比增幅" : " · no relative change"}</span>}</p> : <p>{zh ? "点击查看本周期明细" : "Inspect this period's orders"}</p>}
    </button>
  })}</section>
}
