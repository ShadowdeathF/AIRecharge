"use client"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { Box, LayoutDashboard, ShoppingCart, Package, KeyRound, Users, Palette, CreditCard, Settings, ShieldCheck, ScrollText, FolderTree, ChevronLeft, X, LogOut, ExternalLink, ChartNoAxesCombined } from "lucide-react"
import { useAuth, useLocale, useSiteConfig } from "@/lib/context"
import { cn } from "@/lib/utils"

export const ADMIN_LINKS = [
  { href: "/admin/dashboard", zh: "经营分析", en: "Business overview", icon: LayoutDashboard },
  { href: "/admin/orders", zh: "订单管理", en: "Orders", icon: ShoppingCart },
  { href: "/admin/products", zh: "商品管理", en: "Products", icon: Package },
  { href: "/admin/categories", zh: "商品分类", en: "Categories", icon: FolderTree },
  { href: "/admin/card-keys", zh: "卡密库存", en: "Inventory", icon: KeyRound },
  { href: "/admin/users", zh: "用户管理", en: "Users", icon: Users },
  { href: "/admin/dashboard#workbench-ledger", zh: "对账报表", en: "Transaction ledger", icon: ChartNoAxesCombined },
  { href: "/admin/appearance", zh: "站点外观", en: "Site appearance", icon: Palette },
  { href: "/admin/payment-channels", zh: "支付渠道", en: "Payments", icon: CreditCard },
  { href: "/admin/site-config", zh: "网站设置", en: "Site settings", icon: Settings },
  { href: "/admin/risk", zh: "风控管理", en: "Risk controls", icon: ShieldCheck },
  { href: "/admin/operation-logs", zh: "操作日志", en: "Activity log", icon: ScrollText },
]
export function AdminSidebar({ collapsed, mobileOpen, onCollapse, onClose }: { collapsed: boolean; mobileOpen: boolean; onCollapse: () => void; onClose: () => void }) {
  const pathname = usePathname()
  const { locale } = useLocale()
  const { config } = useSiteConfig()
  const { logout } = useAuth()
  const zh = locale === "zh"
  return <>
    {mobileOpen && <button type="button" className="admin-nav-backdrop" onClick={onClose} aria-label={zh ? "关闭导航" : "Close navigation"} />}
    <aside id="admin-navigation" className={cn("admin-sidebar", collapsed && "is-collapsed", mobileOpen && "is-mobile-open")}>
      <div className="admin-brand-row"><Link href="/" className="admin-brand" title={config?.site_name || "AI Recharge"}>
        {config?.logo_url ? <img src={config.logo_url} alt="" /> : <Box size={27} strokeWidth={1.8} />}<span>{config?.site_name || "AI Recharge"}</span>
      </Link><button type="button" className="admin-sidebar-close" aria-label={zh ? "关闭导航" : "Close navigation"} onClick={onClose}><X size={19} /></button></div>
      <nav className="admin-nav" aria-label={zh ? "后台导航" : "Admin navigation"}>
        {ADMIN_LINKS.map((link,i) => <Link key={link.href} href={link.href} onClick={onClose} title={zh ? link.zh : link.en} aria-current={pathname === link.href ? "page" : undefined} className={cn(pathname === link.href && "active", i === 6 && "admin-nav-section")}><link.icon size={18} strokeWidth={1.7} /><span>{zh ? link.zh : link.en}</span></Link>)}
      </nav>
      <div className="admin-sidebar-footer"><Link href="/"><ExternalLink size={17} /><span>{zh ? "查看商城" : "View store"}</span></Link><button type="button" onClick={logout}><LogOut size={17} /><span>{zh ? "退出登录" : "Sign out"}</span></button><button type="button" className="admin-collapse" onClick={onCollapse} aria-label={collapsed ? (zh ? "展开侧栏" : "Expand sidebar") : (zh ? "收起侧栏" : "Collapse sidebar")}><ChevronLeft size={17} className={cn(collapsed && "rotate-180")} /><span>{zh ? "收起侧栏" : "Collapse"}</span></button></div>
    </aside>
  </>
}
