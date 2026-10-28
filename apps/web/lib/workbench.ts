import { addCalendarDays, businessToday } from "./dashboard"
export type DateRange = { start: string; end: string }
export type Period = "today" | "yesterday" | "7" | "30" | "lastMonth" | "custom"
export function periodRange(period: Exclude<Period, "custom">): DateRange {
  const today = businessToday()
  if (period === "today") return { start: today, end: today }
  if (period === "yesterday") return { start: addCalendarDays(today, -1), end: addCalendarDays(today, -1) }
  if (period === "lastMonth") {
    const end = addCalendarDays(today.slice(0, 7) + "-01", -1)
    return { start: end.slice(0, 7) + "-01", end }
  }
  return { start: addCalendarDays(today, 1 - Number(period)), end: today }
}
export function validRange(range: DateRange) {
  const isDate = (value: string) => /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(value)) && new Date(value + "T00:00:00Z").toISOString().slice(0, 10) === value
  return isDate(range.start) && isDate(range.end) && range.start <= range.end && range.end <= businessToday() && (Date.parse(range.end) - Date.parse(range.start)) / 86400000 < 366
}
export function orderTime(value: string, locale: string, full = false) {
  const date = new Date(/(?:Z|[+-]\d{2}:?\d{2})$/.test(value) ? value : value.replace(" ", "T") + "+08:00")
  return date.toLocaleString(locale === "zh" ? "zh-CN" : "en-GB", { timeZone: "Asia/Shanghai", ...(full ? { year: "numeric" as const } : {}), month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false })
}
export function shortId(value: string) { return value.slice(0, 8) + "…" + value.slice(-4) }
export function selectedAmount(rows: WorkbenchRowAmount[]) { return rows.reduce((sum, row) => sum + Math.round(row.actual_amount * 100), 0) / 100 }
type WorkbenchRowAmount = { actual_amount: number }
