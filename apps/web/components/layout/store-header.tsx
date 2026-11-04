"use client"
import { useEffect, useId, useRef, useState } from "react"
import { LayoutGroup, motion } from "motion/react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { Box, Search, ShoppingCart, Sun, Moon, Monitor, Globe, User, LogOut, Settings, ClipboardList } from "lucide-react"
import { useAuth, useCart, useLocale, useSearch, useSiteConfig, useTheme } from "@/lib/context"
import { cn } from "@/lib/utils"
import { FluidPopover } from "@/components/ui/fluid-popover"

export function StoreHeader({ siteName }: { siteName: string }) {
  const surfaceGroup = useId()
  const appearanceButton = useRef<HTMLButtonElement>(null)
  const userButton = useRef<HTMLButtonElement>(null)
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
    const close = (e: MouseEvent) => {
      const target = e.target as Node
      const panel = document.querySelector('.fluid-popover[aria-hidden="false"]')
      if (!menuRef.current?.contains(target) && !panel?.contains(target)) setMenu(null)
    }
    const escape = (e: KeyboardEvent) => {
      if (e.key !== "Escape" || !menu) return
      setMenu(null)
      const trigger = menu === "appearance" ? appearanceButton : userButton
      trigger.current?.focus({ preventScroll: true })
    }
    document.addEventListener("mousedown", close); document.addEventListener("keydown", escape)
    return () => { document.removeEventListener("mousedown", close); document.removeEventListener("keydown", escape) }
  }, [menu])
  useEffect(() => { setMenu(null) }, [pathname])
  return <LayoutGroup id={surfaceGroup}><header className="store-header"><div className="store-header-inner">
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
        <motion.button ref={appearanceButton} whileTap={{ scale: .91 }} type="button" className="store-icon-button fluid-menu-trigger" aria-label={zh ? "外观设置" : "Appearance"} title={zh ? "外观设置" : "Appearance"} aria-expanded={menu === "appearance"} aria-controls="appearance-menu" onClick={() => setMenu(menu === "appearance" ? null : "appearance")}>{menu !== "appearance" && <motion.span className="fluid-menu-surface" layoutId="appearance-surface" style={{ borderRadius: 22 }} />}{resolvedTheme === "dark" ? <Moon size={19} /> : <Sun size={19} />}</motion.button>
        <FluidPopover open={menu === "appearance"} anchor={appearanceButton} onClose={() => setMenu(null)} surfaceId="appearance-surface" label={zh ? "外观设置" : "Appearance"}>
          <div id="appearance-menu">
          <p className="store-menu-label">{zh ? "显示模式" : "Appearance"}</p>
          <div className="store-theme-options">{([{ key: "light", icon: Sun, label: zh ? "亮色" : "Light" }, { key: "dark", icon: Moon, label: zh ? "暗色" : "Dark" }, { key: "system", icon: Monitor, label: zh ? "跟随系统" : "System" }] as const).map(option => <button key={option.key} type="button" aria-pressed={theme === option.key} onClick={() => { setTheme(option.key); setMenu(null); appearanceButton.current?.focus({ preventScroll: true }) }}><option.icon size={18} />{option.label}</button>)}</div>
          <button type="button" className="store-language-button" onClick={() => setLocale(zh ? "en" : "zh")}><Globe size={16} />{zh ? "English" : "简体中文"}</button>
          </div>
        </FluidPopover>
      </div>
      <Link href="/cart" className="store-icon-button relative" aria-label={t("nav.cart")}><ShoppingCart size={21} />{itemCount > 0 && <span className="store-cart-count">{itemCount > 99 ? "99+" : itemCount}</span>}</Link>
      {!authLoaded ? <span className="h-10 w-14" /> : !isLoggedIn ? <Link href="/login" className="store-login">{t("nav.login")}</Link> : <div className="relative">
        <motion.button ref={userButton} whileTap={{ scale: .91 }} type="button" className="store-icon-button fluid-menu-trigger" aria-label={t("nav.profile")} aria-expanded={menu === "user"} onClick={() => setMenu(menu === "user" ? null : "user")}>{menu !== "user" && <motion.span className="fluid-menu-surface" layoutId="user-surface" style={{ borderRadius: 22 }} />}<User size={20} /></motion.button>
        <FluidPopover open={menu === "user"} anchor={userButton} onClose={() => setMenu(null)} surfaceId="user-surface" label={zh ? "账号菜单" : "Account menu"} className="store-user-menu" width={220}><p className="store-menu-label truncate">{user?.username}</p>
          <Link href="/profile"><User size={16} />{t("nav.profile")}</Link><Link href="/my/orders"><ClipboardList size={16} />{t("nav.myOrders")}</Link>
          {user?.role === "ADMIN" && <Link href="/admin/dashboard"><Settings size={16} />{t("nav.admin")}</Link>}
          <button type="button" onClick={() => { logout(); setMenu(null) }}><LogOut size={16} />{t("nav.logout")}</button>
        </FluidPopover>
      </div>}
    </div>
  </div></header></LayoutGroup>
}
