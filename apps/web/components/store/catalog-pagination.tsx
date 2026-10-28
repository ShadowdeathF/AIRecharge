"use client"
import { ChevronLeft, ChevronRight } from "lucide-react"
import { catalogPageNumbers } from "@/lib/catalog-pagination"
import { useLocale } from "@/lib/context"

export function CatalogPagination({ page, totalPages, totalProducts, mobile, featured, onPage }: { page: number; totalPages: number; totalProducts: number; mobile: boolean; featured: boolean; onPage: (page: number) => void }) {
  const { locale } = useLocale(), zh = locale === "zh"
  if (totalPages <= 1) return null
  return <nav className="store-pagination" aria-label={zh ? "商品分页" : "Product pagination"}>
    <div className="store-pagination-summary"><span>{zh ? <>共 <strong>{totalProducts}</strong> 款商品</> : <><strong>{totalProducts}</strong> products</>}</span><small>{featured ? (zh ? "主推款固定展示" : "Featured product stays visible") : (zh ? "按当前筛选结果展示" : "Showing filtered results")}</small></div>
    <div className="store-pagination-controls">
      <button type="button" className="store-page-direction" aria-label={zh ? "上一页商品" : "Previous product page"} disabled={page <= 1} onClick={() => onPage(page - 1)}><ChevronLeft size={16} /><span>{zh ? "上一页" : "Previous"}</span></button>
      {catalogPageNumbers(page, totalPages, mobile).map((number, index) => number === null ? <span key={`gap-${index}`} className="store-page-gap" aria-hidden="true">…</span> : <button type="button" key={number} aria-label={zh ? `第 ${number} 页商品` : `Product page ${number}`} aria-current={page === number ? "page" : undefined} onClick={() => onPage(number)}>{number}</button>)}
      <button type="button" className="store-page-direction" aria-label={zh ? "下一页商品" : "Next product page"} disabled={page >= totalPages} onClick={() => onPage(page + 1)}><span>{zh ? "下一页" : "Next"}</span><ChevronRight size={16} /></button>
    </div>
    <span className="store-pagination-position" role="status" aria-live="polite" aria-atomic="true">{zh ? "第" : "Page"} <strong>{page}</strong> / {totalPages} {zh ? "页" : ""}</span>
  </nav>
}
