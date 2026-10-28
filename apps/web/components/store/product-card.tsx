"use client"
import Link from "next/link"
import { ArrowRight } from "lucide-react"
import type { ProductCard as ProductCardType } from "@/types"
import { useLocale } from "@/lib/context"
import { cn, getCurrencySymbol } from "@/lib/utils"
import { getProductArtwork } from "@/lib/store-art"
export function ProductCard({ product, priority = false, featured = false }: { product: ProductCardType; priority?: boolean; featured?: boolean }) {
  const { locale, t } = useLocale()
  const zh = locale === "zh"
  const url = `/product/${product.id}`
  return <article className={cn("store-product", featured && "store-product-featured")} data-testid="catalog-product" data-product-id={product.id} data-featured={featured || undefined}>
    <Link href={url} className="store-product-image" aria-label={product.title}><img src={getProductArtwork(product)} alt={product.title} data-merchant-cover={Boolean(product.cover_url)} loading={priority ? "eager" : "lazy"} />{featured && <span className="store-featured-badge">{zh ? "本期主推" : "Featured"}</span>}</Link>
    <div className="store-product-info">
      <Link href={url} className="store-product-title"><h2 title={product.title}>{product.title}</h2></Link>
      <p className="store-product-description">{product.description || ""}</p>
      <div className="store-product-meta">{product.has_specs && <span>{zh ? "多规格可选" : "Multiple options"}</span>}<span className={cn("store-stock", product.stock_available <= 0 && "store-stock-empty")}>{product.stock_available > 0 ? (zh ? "现货" : "In stock") : (zh ? "暂时缺货" : "Out of stock")}</span>{product.delivery_type === "MANUAL" && <span>{zh ? "人工发货" : "Manual delivery"}</span>}</div>
      <div className="store-product-bottom"><p className="store-price"><span>{getCurrencySymbol(product.currency)}</span>{product.base_price.toFixed(2)}{product.has_specs && <small>{t("home.startFrom")}</small>}</p><Link href={url} className="store-product-link store-primary-button" title={zh ? `查看 ${product.title} 详情` : `View ${product.title} details`}><span className="store-product-link-label">{zh ? "查看详情" : "View details"}</span><ArrowRight size={16} /></Link></div>
    </div>
  </article>
}
