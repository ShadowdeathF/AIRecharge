"use client"

import { useEffect, useId, useRef, useState } from "react"
import Link from "next/link"
import { LayoutGroup, motion } from "motion/react"
import { ArrowLeftRight, Check, ChevronDown, ExternalLink, Globe, LogOut, Monitor, Moon, Sun, User } from "lucide-react"
import { FluidPopover } from "@/components/ui/fluid-popover"
import { useAuth, useLocale, useTheme } from "@/lib/context"

export function AdminHeaderActions() {
  const group = useId()
  const appearance = useRef<HTMLButtonElement>(null)
  const account = useRef<HTMLButtonElement>(null)
  const tools = useRef<HTMLDivElement>(null)
  const [menu, setMenu] = useState<"appearance" | "account" | null>(null)
  const { theme, resolvedTheme, setTheme } = useTheme()
  const { locale, setLocale } = useLocale()
  const { user, logout } = useAuth()
  const zh = locale === "zh"

  useEffect(() => {
    const outside = (event: MouseEvent) => {
      const panel = document.querySelector('.admin-header-popover[aria-hidden="false"]')
      if (!tools.current?.contains(event.target as Node) && !panel?.contains(event.target as Node)) setMenu(null)
    }
    const escape = (event: KeyboardEvent) => {
      if (event.key !== "Escape" || !menu) return
      setMenu(null)
      const trigger = menu === "appearance" ? appearance : account
      trigger.current?.focus({ preventScroll: true })
    }
    document.addEventListener("mousedown", outside)
    document.addEventListener("keydown", escape)
    return () => {
      document.removeEventListener("mousedown", outside)
      document.removeEventListener("keydown", escape)
    }
  }, [menu])

  const endSession = () => { setMenu(null); logout() }
  const options = [
    { key: "light", icon: Sun, title: zh ? "亮色" : "Light", description: zh ? "清透、明亮的工作界面" : "A bright, clear workspace" },
    { key: "dark", icon: Moon, title: zh ? "暗色" : "Dark", description: zh ? "柔和的石墨色阅读界面" : "A soft graphite workspace" },
    { key: "system", icon: Monitor, title: zh ? "跟随系统" : "System", description: zh ? "随设备的显示模式自动切换" : "Match your device appearance" },
  ] as const

  return <LayoutGroup id={group}>
    <div className="admin-topbar-tools" ref={tools}>
      <Link href="/" className="admin-view-store" title={zh ? "查看商城" : "View store"}>{zh ? "查看商城" : "View store"}<ExternalLink size={13} /></Link>
      <button type="button" className="admin-language" aria-label={zh ? "切换语言" : "Change language"} title={zh ? "简体中文 · Switch to English" : "English · 切换为简体中文"} onClick={() => setLocale(zh ? "en" : "zh")}><Globe size={17} /><span>{zh ? "中" : "EN"}</span></button>
      <motion.button ref={appearance} type="button" className="fluid-menu-trigger admin-appearance-trigger" whileTap={{ scale: .93 }} aria-label={zh ? "外观设置" : "Appearance"} title={zh ? "外观设置" : "Appearance"} aria-expanded={menu === "appearance"} aria-controls={`${group}-appearance`} onClick={() => setMenu(menu === "appearance" ? null : "appearance")}>
        {menu !== "appearance" && <motion.span className="fluid-menu-surface" layoutId="admin-appearance" style={{ borderRadius: 14 }} />}
        {theme === "system" ? <Monitor size={19} /> : resolvedTheme === "dark" ? <Moon size={19} /> : <Sun size={19} />}
      </motion.button>
      <FluidPopover open={menu === "appearance"} anchor={appearance} surfaceId="admin-appearance" label={zh ? "外观设置" : "Appearance"} onClose={() => setMenu(null)} className="admin-header-popover admin-appearance-popover" width={300}>
        <div id={`${group}-appearance`}>
          <p className="store-menu-label">{zh ? "工作台外观" : "Workspace appearance"}</p>
          <div className="admin-appearance-options">{options.map(option => <button key={option.key} type="button" aria-pressed={theme === option.key} onClick={() => {
            if (theme !== option.key) setTheme(option.key)
            setMenu(null)
            appearance.current?.focus({ preventScroll: true })
          }}><option.icon size={19} /><span><strong>{option.title}</strong><small>{option.description}</small></span>{theme === option.key && <Check size={17} />}</button>)}</div>
          <p className="admin-menu-note">{zh ? "配色方案由「站点外观」统一管理" : "Brand colors are managed in Site appearance"}</p>
        </div>
      </FluidPopover>
      <motion.button ref={account} type="button" className="admin-user admin-account-trigger" whileTap={{ scale: .97 }} aria-label={zh ? "账号菜单" : "Account menu"} aria-expanded={menu === "account"} aria-controls={`${group}-account`} onClick={() => setMenu(menu === "account" ? null : "account")}>
        <span className="admin-avatar"><User size={17} /></span><span className="admin-username">{user?.username || (zh ? "管理员" : "Admin")}</span><ChevronDown size={13} />
      </motion.button>
      <FluidPopover open={menu === "account"} anchor={account} surfaceId="admin-account" label={zh ? "账号菜单" : "Account menu"} onClose={() => setMenu(null)} className="admin-header-popover admin-account-popover" width={280}>
        <div id={`${group}-account`}>
          <div className="admin-account-summary"><span className="admin-avatar"><User size={21} /></span><div><strong>{user?.username}</strong><small>{user?.email}</small></div><span className="admin-role">{zh ? "管理员" : "Admin"}</span></div>
          <div className="admin-account-links">
            <Link href="/profile" onClick={() => setMenu(null)}><User size={17} /><span>{zh ? "个人资料" : "My profile"}</span></Link>
            <button type="button" onClick={endSession}><ArrowLeftRight size={17} /><span>{zh ? "切换账号" : "Switch account"}</span></button>
            <button type="button" className="admin-signout" onClick={endSession}><LogOut size={17} /><span>{zh ? "退出登录" : "Sign out"}</span></button>
          </div>
        </div>
      </FluidPopover>
    </div>
  </LayoutGroup>
}
