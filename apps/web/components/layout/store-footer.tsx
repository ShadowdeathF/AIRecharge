"use client"
import Link from "next/link"
import { ClipboardList, Mail, Github, ArrowUpRight } from "lucide-react"
import { useLocale, useSiteConfig } from "@/lib/context"
export function StoreFooter() {
  const { locale, t } = useLocale()
  const { config } = useSiteConfig()
  return <footer className="store-footer"><div className="store-footer-inner">
    <p>{config?.footer_text || "Orion Key"}</p><div className="store-footer-links">
      <Link href="/order/query"><ClipboardList size={15} />{t("nav.orders")}</Link>
      {config?.contact_email && <a href={`mailto:${config.contact_email}`}><Mail size={15} />{locale === "zh" ? "联系客服" : "Contact support"}</a>}
      {config?.contact_telegram && <a href={config.contact_telegram} target="_blank" rel="noopener noreferrer">Telegram<ArrowUpRight size={14} /></a>}
      {config?.github_url && <a href={config.github_url} target="_blank" rel="noopener noreferrer" aria-label="GitHub"><Github size={17} /></a>}
    </div>
  </div></footer>
}
