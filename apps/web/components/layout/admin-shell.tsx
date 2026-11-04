"use client"
import { useCallback, useEffect, useState, type ReactNode } from "react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { Menu, Search, X } from "lucide-react"
import { AdminSidebar, ADMIN_LINKS } from "./admin-sidebar"
import { useLocale } from "@/lib/context"
import { cn } from "@/lib/utils"
import { PageTransition } from "./page-transition"
import { AdminHeaderActions } from "./admin-header-actions"
export function AdminShell({ children }: { children: ReactNode }) {
  const [collapsed, setCollapsed] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [search, setSearch] = useState("")
  const closeMobile = useCallback(() => setMobileOpen(false), [])
  const { locale } = useLocale()
  const pathname = usePathname()
  const zh = locale === "zh"
  const link = ADMIN_LINKS.find(l => pathname === l.href)
  useEffect(() => { setCollapsed(localStorage.getItem("admin-sidebar-collapsed") === "true") }, [])
  const toggleCollapsed = () => {
    const next = !collapsed
    setCollapsed(next)
    localStorage.setItem("admin-sidebar-collapsed", String(next))
  }
  useEffect(() => {
    setMobileOpen(false)
    setSearch(new URLSearchParams(window.location.search).get("keyword") || "")
  }, [pathname])
  return <div className={cn("admin-shell", collapsed && "admin-collapsed")}>
    <AdminSidebar collapsed={collapsed} mobileOpen={mobileOpen} onCollapse={toggleCollapsed} onClose={closeMobile} />
    <div className="admin-workspace" inert={mobileOpen}>
      <header className="admin-topbar">
        <button type="button" className="admin-mobile-toggle" onClick={() => setMobileOpen(true)} aria-label={zh ? "打开导航" : "Open navigation"} aria-expanded={mobileOpen} aria-controls="admin-navigation"><Menu size={21} /></button>
        <nav className="admin-breadcrumb" aria-label={zh ? "面包屑" : "Breadcrumb"}><Link href="/admin/dashboard">{zh ? "工作台" : "Workspace"}</Link><span aria-hidden="true">/</span><strong aria-current="page">{link ? (zh ? link.zh : link.en) : (zh ? "管理后台" : "Admin")}</strong></nav>
        <form action="/admin/orders" className="admin-global-search" role="search" onSubmit={event => { if (!search.trim()) event.preventDefault() }}><Search size={15} /><input name="keyword" aria-label={zh ? "全局搜索订单" : "Search all orders"} placeholder={zh ? "搜索订单、商品名称…" : "Search orders or products…"} value={search} maxLength={100} onChange={event => setSearch(event.target.value)} />{search && <button type="button" aria-label={zh ? "清空全局搜索" : "Clear global search"} onClick={() => setSearch("")}><X size={14} /></button>}<button type="submit" aria-label={zh ? "提交全局搜索" : "Search"}><Search size={14} /></button></form>
        <AdminHeaderActions key={pathname + (mobileOpen ? "-navigation" : "")} />
      </header>
      <PageTransition className="admin-main">{children}</PageTransition>
    </div>
  </div>
}
