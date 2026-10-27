"use client"
import type { ReactNode } from "react"
import { AdminShell } from "@/components/layout/admin-shell"
import { useRequireAdmin } from "@/lib/hooks"
import "./admin.css"
export default function AdminLayout({ children }: { children: ReactNode }) {
  const user = useRequireAdmin()
  if (!user) return null
  return <AdminShell>{children}</AdminShell>
}
