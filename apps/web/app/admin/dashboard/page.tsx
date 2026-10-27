"use client"
import { useEffect, useState } from "react"
import { useLocale } from "@/lib/context"
import { DashboardStats, LowStockAlert, VisitOverview } from "@/components/admin/dashboard-stats"
import { SalesChart } from "@/components/admin/sales-chart"
import { RecentOrders } from "@/components/admin/recent-orders"
import { adminDashboardApi, adminOrderApi } from "@/services/api"
import { addCalendarDays, businessToday, fillSalesDates } from "@/lib/dashboard"
import type { DashboardStats as Stats, SalesTrend, AdminOrderItem } from "@/types"
export default function AdminDashboardPage() {
  const { locale } = useLocale(); const zh = locale === "zh"
  const [stats, setStats] = useState<Stats | null>(null), [orders, setOrders] = useState<AdminOrderItem[]>([])
  const [loading, setLoading] = useState(true), [error, setError] = useState("")
  const [refresh, setRefresh] = useState(0)
  const [range, setRange] = useState(() => { const end=businessToday(); return { start: addCalendarDays(end,-6), end } })
  const [trends, setTrends] = useState<SalesTrend[]>([]), [trendLoading, setTrendLoading] = useState(true), [trendError, setTrendError] = useState("")
  useEffect(() => {
    let cancelled=false; setLoading(true); setError("")
    Promise.all([adminDashboardApi.getStats(),adminOrderApi.getList({page:1,page_size:4})]).then(([data,result])=>{if(!cancelled){setStats(data);setOrders(result.list)}}).catch(()=>{if(!cancelled)setError(zh?"经营数据加载失败，请重试。":"Unable to load business data. Please retry.")}).finally(()=>{if(!cancelled)setLoading(false)})
    return ()=>{cancelled=true}
  },[refresh,zh])
  useEffect(()=>{
    let cancelled=false;setTrendLoading(true);setTrendError("")
    adminDashboardApi.getSalesTrend({period:"daily",start_date:range.start,end_date:range.end}).then(rows=>{if(!cancelled)setTrends(fillSalesDates(rows,range.start,range.end))}).catch(()=>{if(!cancelled){setTrends([]);setTrendError(zh?"趋势数据加载失败，请刷新重试。":"Unable to load trend data.")}}).finally(()=>{if(!cancelled)setTrendLoading(false)})
    return()=>{cancelled=true}
  },[range,refresh,zh])
  return <div className="analytics-page" data-testid="admin-analytics">
    <div className="analytics-page-heading"><div><h1>{zh?"经营分析":"Business overview"}</h1><p>{zh?`今天是 ${businessToday().replaceAll("-","/")}，以下为店铺经营数据概览。`:`Business overview for ${businessToday()}.`}</p></div><button type="button" className="admin-text-button" onClick={()=>setRefresh(v=>v+1)} disabled={loading}>{zh?"刷新数据":"Refresh"}</button></div>
    {loading && !stats ? <div className="admin-loading">{zh?"正在加载经营数据…":"Loading business data…"}</div> : error ? <div className="admin-error" role="alert">{error}<button type="button" onClick={()=>setRefresh(v=>v+1)}>{zh?"重试":"Retry"}</button></div> : stats && <>
      <DashboardStats stats={stats} />
      <div className="analytics-body"><SalesChart trends={trends} range={range} onRangeChange={setRange} loading={trendLoading} error={trendError} /><div className="analytics-aside"><LowStockAlert products={stats.low_stock_products} /><VisitOverview stats={stats} /></div></div>
      <RecentOrders orders={orders} />
    </>}
  </div>
}
