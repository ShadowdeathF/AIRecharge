"use client"
import { useEffect, useState, type ReactNode } from "react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { Menu, Sun, Moon, Monitor, Globe, ExternalLink, User } from "lucide-react"
import { AdminSidebar, ADMIN_LINKS } from "./admin-sidebar"
import { useAuth, useLocale, useTheme } from "@/lib/context"
import { cn } from "@/lib/utils"
export function AdminShell({ children }: { children: ReactNode }) {
  const [collapsed, setCollapsed] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const { locale, setLocale } = useLocale()
  const { theme, setTheme } = useTheme()
  const { user } = useAuth()
  const pathname = usePathname()
  const zh = locale === "zh"
  const link = ADMIN_LINKS.find(l => pathname === l.href)
  useEffect(() => { setMobileOpen(false) }, [pathname])
  useEffect(() => {
    if (!mobileOpen) return
    const previous = document.body.style.overflow
    document.body.style.overflow = "hidden"
    const close = (e: KeyboardEvent) => { if (e.key === "Escape") setMobileOpen(false) }
    document.addEventListener("keydown", close)
    return () => { document.body.style.overflow = previous; document.removeEventListener("keydown", close) }
  }, [mobileOpen])
  return <div className={cn("admin-shell", collapsed && "admin-collapsed")}>
    <AdminSidebar collapsed={collapsed} mobileOpen={mobileOpen} onCollapse={() => setCollapsed(!collapsed)} onClose={() => setMobileOpen(false)} />
    <div className="admin-workspace">
      <header className="admin-topbar">
        <button type="button" className="admin-mobile-toggle" onClick={() => setMobileOpen(true)} aria-label={zh ? "打开导航" : "Open navigation"} aria-expanded={mobileOpen} aria-controls="admin-navigation"><Menu size={21} /></button>
        <div className="admin-breadcrumb"><span>{zh ? "工作台" : "Workspace"}</span><span>/</span><strong>{link ? (zh ? link.zh : link.en) : (zh ? "管理后台" : "Admin")}</strong></div>
        <div className="admin-topbar-tools">
          <Link href="/" className="admin-view-store">{zh ? "查看商城" : "View store"}<ExternalLink size={13} /></Link>
          <button type="button" aria-label={zh ? "切换语言" : "Change language"} title={zh ? "English" : "简体中文"} onClick={() => setLocale(zh ? "en" : "zh")}><Globe size={17} /></button>
          <select aria-label={zh ? "显示模式" : "Appearance mode"} value={theme} onChange={e => setTheme(e.target.value as "light" | "dark" | "system")}><option value="light">{zh ? "亮色" : "Light"}</option><option value="dark">{zh ? "暗色" : "Dark"}</option><option value="system">{zh ? "跟随系统" : "System"}</option></select>
          <span className="admin-user"><span className="admin-avatar"><User size={17} /></span><span>{user?.username || (zh ? "管理员" : "Admin")}</span></span>
        </div>
      </header>
      <main className="admin-main">{children}</main>
    </div>
  </div>
}
