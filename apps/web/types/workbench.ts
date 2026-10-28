export interface WorkbenchSummary {
  sales_amount: number
  order_count: number
  average_order: number
  undelivered_count: number
}
export interface WorkbenchTrend {
  date: string
  sales_amount: number
  order_count: number
  previous_sales_amount: number
  previous_order_count: number
}
export interface WorkbenchOverview {
  start_date: string
  end_date: string
  summary: WorkbenchSummary
  comparison: { start_date: string; end_date: string; summary: WorkbenchSummary }
  trend: WorkbenchTrend[]
  low_stock_products: { product_id: string; title: string; available_stock: number; threshold: number }[]
  reconciliation_status: "NOT_IMPORTED"
}
export interface WorkbenchOrder {
  id: string
  payment_reference: string | null
  paid_at: string
  created_at: string
  actual_amount: number
  status: "PAID" | "DELIVERED"
  payment_method: string | null
  product_title: string
  item_count: number
}
export interface WorkbenchLedger {
  list: WorkbenchOrder[]
  pagination: { page: number; page_size: number; total: number }
  totals: { sales_amount: number; order_count: number }
}
export interface WorkbenchFilters {
  start_date: string
  end_date: string
  payment_method?: string
  status?: string
  keyword?: string
}
