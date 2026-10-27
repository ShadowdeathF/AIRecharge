"use client"
import { useEffect, useRef, useState } from "react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { Box, Search, ShoppingCart, Sun, Moon, Monitor, Globe, User, LogOut, Settings, ClipboardList } from "lucide-react"
import { useAuth, useCart, useLocale, useSearch, useSiteConfig, useTheme } from "@/lib/context"
import { cn } from "@/lib/utils"

export function StoreHeader({ siteName }: { siteName: string }) {
  const { t, locale, setLocale } = useLocale()
  const { theme, resolvedTheme, setTheme } = useTheme()
  const { isLoggedIn, user, logout, authLoaded } = useAuth()
  const { itemCount } = useCart()
  const { config } = useSiteConfig()
  const { searchQuery, setSearchQuery } = useSearch()
  const pathname = usePathname()
  const router = useRouter()
  const [menu, setMenu] = useState<"appearance" | "user" | null>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const zh = locale === "zh"
  useEffect(() => {
    const close = (e: MouseEvent) => { if (!menuRef.current?.contains(e.target as Node)) setMenu(null) }
    const escape = (e: KeyboardEvent) => { if (e.key === "Escape") setMenu(null) }
    document.addEventListener("mousedown", close); document.addEventListener("keydown", escape)
    return () => { document.removeEventListener("mousedown", close); document.removeEventListener("keydown", escape) }
  }, [])
  useEffect(() => { setMenu(null) }, [pathname])
  return <header className="store-header"><div className="store-header-inner">
    <Link href="/" className="store-brand" aria-label={siteName || "Orion Key"}>
      {config?.logo_url ? <img src={config.logo_url} alt="" className="h-7 w-7 object-contain" /> : <Box className="h-7 w-7 text-primary" strokeWidth={1.8} />}
      <span>{siteName || config?.site_name || "Orion Key"}</span>
    </Link>
    <nav className="store-desktop-nav" aria-label={zh ? "主导航" : "Main navigation"}>
      <Link href="/" className={cn(pathname === "/" && "active")} aria-current={pathname === "/" ? "page" : undefined}>{zh ? "商城" : "Store"}</Link>
      <Link href="/order/query" className={cn(pathname === "/order/query" && "active")} aria-current={pathname === "/order/query" ? "page" : undefined}>{t("nav.orders")}</Link>
    </nav>
    <form className="store-header-search" role="search" onSubmit={e => { e.preventDefault(); if (pathname !== "/") router.push("/"); else document.getElementById("catalog")?.scrollIntoView({ behavior: "smooth" }) }}>
      <Search size={16} aria-hidden="true" /><input aria-label={zh ? "全站商品搜索" : "Search catalog"} placeholder={t("home.search")} value={searchQuery} onChange={e => setSearchQuery(e.target.value)} />
    </form>
    <div className="store-header-actions" ref={menuRef}>
      <div className="relative">
        <button type="button" className="store-icon-button" aria-label={zh ? "外观设置" : "Appearance"} title={zh ? "外观设置" : "Appearance"} aria-expanded={menu === "appearance"} aria-controls="appearance-menu" onClick={() => setMenu(menu === "appearance" ? null : "appearance")}>{resolvedTheme === "dark" ? <Moon size={19} /> : <Sun size={19} />}</button>
        {menu === "appearance" && <section id="appearance-menu" className="store-popover" aria-label={zh ? "外观设置" : "Appearance"}>
          <p className="store-menu-label">{zh ? "显示模式" : "Appearance"}</p>
          <div className="store-theme-options">{([{ key: "light", icon: Sun, label: zh ? "亮色" : "Light" }, { key: "dark", icon: Moon, label: zh ? "暗色" : "Dark" }, { key: "system", icon: Monitor, label: zh ? "跟随系统" : "System" }] as const).map(option => <button key={option.key} type="button" aria-pressed={theme === option.key} onClick={() => { setTheme(option.key); setMenu(null) }}><option.icon size={18} />{option.label}</button>)}</div>
          <button type="button" className="store-language-button" onClick={() => setLocale(zh ? "en" : "zh")}><Globe size={16} />{zh ? "English" : "简体中文"}</button>
        </section>}
      </div>
      <Link href="/cart" className="store-icon-button relative" aria-label={t("nav.cart")}><ShoppingCart size={21} />{itemCount > 0 && <span className="store-cart-count">{itemCount > 99 ? "99+" : itemCount}</span>}</Link>
      {!authLoaded ? <span className="h-10 w-14" /> : !isLoggedIn ? <Link href="/login" className="store-login">{t("nav.login")}</Link> : <div className="relative">
        <button type="button" className="store-icon-button" aria-label={t("nav.profile")} aria-expanded={menu === "user"} onClick={() => setMenu(menu === "user" ? null : "user")}><User size={20} /></button>
        {menu === "user" && <div className="store-popover store-user-menu"><p className="store-menu-label truncate">{user?.username}</p>
          <Link href="/profile"><User size={16} />{t("nav.profile")}</Link><Link href="/my/orders"><ClipboardList size={16} />{t("nav.myOrders")}</Link>
          {user?.role === "ADMIN" && <Link href="/admin/dashboard"><Settings size={16} />{t("nav.admin")}</Link>}
          <button type="button" onClick={() => { logout(); setMenu(null) }}><LogOut size={16} />{t("nav.logout")}</button>
        </div>}
      </div>}
    </div>
  </div></header>
}
