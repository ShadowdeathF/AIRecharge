"use client"
import Link from "next/link"
import { ArrowRight } from "lucide-react"
import type { ProductCard as ProductCardType } from "@/types"
import { useLocale } from "@/lib/context"
import { cn, getCurrencySymbol } from "@/lib/utils"
import { getProductArtwork } from "@/lib/store-art"
export function ProductCard({ product, featured = false }: { product: ProductCardType; featured?: boolean }) {
  const { locale, t } = useLocale()
  const zh = locale === "zh"
  const url = `/product/${product.id}`
  return <article className={cn("store-product", featured ? "store-product-featured" : "store-product-row")} data-testid={featured ? "featured-product" : "catalog-product"}>
    <Link href={url} className="store-product-image" aria-label={product.title}><img src={getProductArtwork(product)} alt={product.title} data-merchant-cover={Boolean(product.cover_url)} loading={featured ? "eager" : "lazy"} /></Link>
    <div className="store-product-info">
      <Link href={url} className="store-product-title"><h2>{product.title}</h2></Link>
      {product.description && <p className="store-product-description">{product.description}</p>}
      <div className="store-product-meta">{product.has_specs && <span>{zh ? "多规格可选" : "Multiple options"}</span>}<span className={cn("store-stock", product.stock_available <= 0 && "store-stock-empty")}>{product.stock_available > 0 ? (zh ? "现货" : "In stock") : (zh ? "暂时缺货" : "Out of stock")}</span>{product.delivery_type === "MANUAL" && <span>{zh ? "人工发货" : "Manual delivery"}</span>}</div>
      <div className="store-product-bottom"><p className="store-price"><span>{getCurrencySymbol(product.currency)}</span>{product.base_price.toFixed(2)}{product.has_specs && <small>{t("home.startFrom")}</small>}</p><Link href={url} className={cn("store-product-link", featured && "store-primary-button")}>{zh ? "查看详情" : "View details"}<ArrowRight size={16} /></Link></div>
    </div>
  </article>
}
