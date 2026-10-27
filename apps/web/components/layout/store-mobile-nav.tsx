"use client"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { House, ShoppingCart, ClipboardList, User } from "lucide-react"
import { useCart, useLocale } from "@/lib/context"
import { cn } from "@/lib/utils"
export function StoreMobileNav() {
  const pathname = usePathname()
  const { locale } = useLocale()
  const { itemCount } = useCart()
  const zh = locale === "zh"
  const links = [
    { href: "/", label: zh ? "商城" : "Store", icon: House, active: pathname === "/" || pathname.startsWith("/product/") },
    { href: "/cart", label: zh ? "购物车" : "Cart", icon: ShoppingCart, active: pathname === "/cart" || pathname === "/checkout" },
    { href: "/order/query", label: zh ? "查订单" : "Orders", icon: ClipboardList, active: pathname.startsWith("/order/") || pathname === "/my/orders" },
    { href: "/profile", label: zh ? "我的" : "Account", icon: User, active: ["/profile", "/login", "/register"].includes(pathname) },
  ]
  return <nav className="store-mobile-nav" data-testid="mobile-nav" aria-label={zh ? "底部导航" : "Mobile navigation"}>{links.map(link => <Link key={link.href} href={link.href} className={cn(link.active && "active")} aria-current={link.active ? "page" : undefined}>
    <span className="relative"><link.icon size={21} strokeWidth={link.active ? 2.3 : 1.7} />{link.href === "/cart" && itemCount > 0 && <span className="store-cart-count">{itemCount > 99 ? "99+" : itemCount}</span>}</span><span>{link.label}</span>
  </Link>)}</nav>
}
