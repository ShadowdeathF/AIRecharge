import type { SalesTrend } from "@/types"
export function businessToday() {
  const parts = new Intl.DateTimeFormat("en", { timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(new Date())
  return `${parts.find(p=>p.type==="year")?.value}-${parts.find(p=>p.type==="month")?.value}-${parts.find(p=>p.type==="day")?.value}`
}
export function addCalendarDays(key: string, days: number) {
  const date = new Date(key + "T00:00:00Z"); date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0,10)
}
export function fillSalesDates(trends: SalesTrend[], start: string, end: string): SalesTrend[] {
  const byDate = new Map(trends.map(row => [row.date, row]))
  const result: SalesTrend[] = []
  for (let date = start; date <= end && result.length < 366; date = addCalendarDays(date,1)) {
    result.push(byDate.get(date) || { date, sales_amount: 0, order_count: 0 })
  }
  return result
}
export function money(value: number) { return new Intl.NumberFormat("zh-CN", { style: "currency", currency: "CNY", minimumFractionDigits: 2 }).format(value) }
