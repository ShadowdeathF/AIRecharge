"use client"
import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react"
import { Search, SlidersHorizontal, ShoppingCart, CreditCard, Mail, ChevronRight, X } from "lucide-react"
import { useLocale, useSiteConfig, useSearch, type SortKey } from "@/lib/context"
import { ProductCard } from "@/components/store/product-card"
import { CatalogPagination } from "@/components/store/catalog-pagination"
import { paginateCatalog } from "@/lib/catalog-pagination"
import { cn } from "@/lib/utils"
import type { ProductCard as Product, Category } from "@/types"

interface HomeContentProps { products: Product[]; categories: Category[]; siteSlogan: string; siteDescription: string }
const mobileQuery = "(max-width: 600px)"
const subscribeMobile = (callback: () => void) => { const query = window.matchMedia(mobileQuery); query.addEventListener("change", callback); return () => query.removeEventListener("change", callback) }
const getMobile = () => window.matchMedia(mobileQuery).matches
const getServerMobile = () => false

export function HomeContent({ products, categories, siteSlogan, siteDescription }: HomeContentProps) {
  const { locale, t } = useLocale()
  const zh = locale === "zh"
  const { config } = useSiteConfig()
  const { searchQuery, setSearchQuery, sortBy, setSortBy, inStockOnly, setInStockOnly, priceMin, setPriceMin, priceMax, setPriceMax } = useSearch()
  const [category, setCategory] = useState<string | null>(null)
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [page, setPage] = useState(1)
  const catalog = useRef<HTMLElement>(null)
  const mobile = useSyncExternalStore(subscribeMobile, getMobile, getServerMobile)
  const pageSize = mobile ? 3 : 6
  useEffect(() => setPage(1), [category, searchQuery, sortBy, inStockOnly, priceMin, priceMax, pageSize])
  const filtered = useMemo(() => {
    let result = products.filter(p => (!category || p.category_id === category) && (!searchQuery.trim() || p.title.toLowerCase().includes(searchQuery.trim().toLowerCase())) && (!inStockOnly || p.stock_available > 0))
    const min = Number.parseFloat(priceMin), max = Number.parseFloat(priceMax)
    if (!Number.isNaN(min)) result = result.filter(p => p.base_price >= min)
    if (!Number.isNaN(max)) result = result.filter(p => p.base_price <= max)
    switch (sortBy) {
      case "hot": result.sort((a,b) => (b.sales_count || 0) + (b.initial_sales || 0) - (a.sales_count || 0) - (a.initial_sales || 0)); break
      case "price_low": result.sort((a,b) => a.base_price - b.base_price); break
      case "price_high": result.sort((a,b) => b.base_price - a.base_price); break
      case "new": result.sort((a,b) => new Date(b.created_at || 0).getTime() - new Date(a.created_at || 0).getTime()); break
    }
    return result
  }, [products, category, searchQuery, inStockOnly, priceMin, priceMax, sortBy])
  const activeFilters = Boolean(inStockOnly || priceMin || priceMax)
  // Default merchandising follows the merchant's product order within each category.
  // Search, filters and explicit sorting keep every matching result compact and in order.
  const featured = filtered.length > 0 && sortBy === "default" && !searchQuery.trim() && !activeFilters ? filtered[0] : null
  const compactProducts = featured ? filtered.slice(1) : filtered
  const pagination = paginateCatalog(compactProducts, page, pageSize)
  const changePage = (next: number) => {
    setPage(next)
    catalog.current?.scrollIntoView({ block: "start", behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" })
  }
  const reset = () => { setCategory(null); setSearchQuery(""); setInStockOnly(false); setPriceMin(""); setPriceMax(""); setSortBy("default") }
  // The local starter's generic English copy is replaced, merchant-written copy is preserved.
  const heading = !siteSlogan || siteSlogan === "Instant Keys, Anytime" ? (zh ? "你的下一份 AI 生产力。" : "Your next AI advantage.") : siteSlogan
  const description = !siteDescription || siteDescription === "Automated delivery, available 24/7." ? (zh ? "精选数字商品，让每一次选择都更简单。" : "Curated digital goods. A simpler way to choose.") : siteDescription
  const steps = [
    { icon: ShoppingCart, title: zh ? "选择商品" : "Choose a product", detail: zh ? "找到适合你的数字商品。" : "Find the right digital product." },
    { icon: CreditCard, title: zh ? "完成支付" : "Complete payment", detail: zh ? "确认规格，选择付款方式。" : "Choose your options and payment method." },
    { icon: Mail, title: zh ? "查看交付" : "View your delivery", detail: zh ? "在订单页查看卡密与交付状态。" : "View keys and delivery status in your order." },
  ]
  const heroImage = config?.hero_image_url || "/images/store/hero-key.webp"
  const heroContent = <>
    <picture>{config?.hero_mobile_image_url && <source media="(max-width: 600px)" srcSet={config.hero_mobile_image_url} />}<img className="store-hero-art" src={heroImage} alt="" fetchPriority="high" /></picture>
    <div className="store-hero-copy"><span className="store-eyebrow">DIGITAL GOODS, SIMPLY.</span><h1>{heading}</h1><p>{description}</p></div>
  </>
  return <div className="store-home">
    {config?.hero_enabled !== false && <section className="store-hero" data-testid="store-hero">
      {config?.hero_link_url ? <a href={config.hero_link_url} className="block h-full" aria-label={zh ? "查看横幅推荐" : "Explore featured content"}>{heroContent}</a> : heroContent}
    </section>}
    <section ref={catalog} id="catalog" className="store-catalog" aria-label={zh ? "商品目录" : "Product catalog"}>
      <div className="store-catalog-toolbar">
        <div className="store-categories" role="group" aria-label={zh ? "商品分类" : "Categories"}>
          <button type="button" onClick={() => setCategory(null)} aria-pressed={!category} className={cn(!category && "active")}>{t("home.allCategories")}</button>
          {categories.map(cat => <button type="button" key={cat.id} onClick={() => setCategory(cat.id)} aria-pressed={category === cat.id} className={cn(category === cat.id && "active")}>{cat.name}</button>)}
        </div>
        <div className="store-catalog-actions">
          <label className="store-catalog-search"><Search size={16} aria-hidden="true" /><input type="search" aria-label={zh ? "搜索商品" : "Search products"} placeholder={zh ? "搜索商品名称" : "Search products"} value={searchQuery} onChange={e => setSearchQuery(e.target.value)} /></label>
          <button type="button" className={cn("store-filter-toggle", (filtersOpen || activeFilters) && "active")} aria-expanded={filtersOpen} aria-controls="catalog-filters" onClick={() => setFiltersOpen(!filtersOpen)}><SlidersHorizontal size={16} /><span>{zh ? "筛选" : "Filters"}</span>{activeFilters && <span className="store-filter-dot" />}</button>
        </div>
      </div>
      {filtersOpen && <div id="catalog-filters" className="store-filters">
        <label>{zh ? "排序" : "Sort"}<select aria-label={zh ? "商品排序" : "Sort products"} value={sortBy} onChange={e => setSortBy(e.target.value as SortKey)}><option value="default">{t("home.sortDefault")}</option><option value="hot">{t("home.sortHot")}</option><option value="price_low">{t("home.sortPriceLow")}</option><option value="price_high">{t("home.sortPriceHigh")}</option><option value="new">{t("home.sortNew")}</option></select></label>
        <label>{zh ? "最低价格" : "Minimum price"}<input type="number" min="0" aria-label={zh ? "最低价格" : "Minimum price"} placeholder="0" value={priceMin} onChange={e => setPriceMin(e.target.value)} /></label>
        <label>{zh ? "最高价格" : "Maximum price"}<input type="number" min="0" aria-label={zh ? "最高价格" : "Maximum price"} placeholder={zh ? "不限" : "Any"} value={priceMax} onChange={e => setPriceMax(e.target.value)} /></label>
        <label className="store-stock-filter"><input type="checkbox" checked={inStockOnly} onChange={e => setInStockOnly(e.target.checked)} />{t("home.filterInStock")}</label>
        <button type="button" className="store-reset" onClick={reset}><X size={15} />{zh ? "重置筛选" : "Reset filters"}</button>
      </div>}
      {filtered.length > 0 ? <div className={cn("store-merchandise", !featured && "store-merchandise-compact")} data-testid="catalog-grid">
        {featured && <ProductCard product={featured} featured priority />}
        <div className="store-product-list">
          {pagination.items.map((product, index) => <ProductCard key={product.id} product={product} priority={!featured && index === 0} />)}
        </div>
      </div> : <div className="store-empty" data-testid="catalog-empty"><Search size={36} strokeWidth={1.3} /><h2>{t("home.noProducts")}</h2><p>{zh ? "试试其他关键词，或调整筛选条件。" : "Try another keyword or adjust your filters."}</p><button type="button" onClick={reset}>{zh ? "重置筛选" : "Reset filters"}</button></div>}
      <CatalogPagination page={pagination.page} totalPages={pagination.totalPages} totalProducts={filtered.length} mobile={mobile} featured={Boolean(featured)} onPage={changePage} />
    </section>
    <section className="store-purchase-steps" aria-label={zh ? "购买流程" : "How it works"}>
      {steps.map((step,i) => <div className="store-step" key={step.title}><span className="store-step-icon"><step.icon size={23} strokeWidth={1.7} /></span><div><h3><span>{i+1}</span>{step.title}</h3><p>{step.detail}</p></div>{i<2 && <ChevronRight className="store-step-arrow" size={17} />}</div>)}
    </section>
  </div>
}
