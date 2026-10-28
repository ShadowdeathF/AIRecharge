"use client"
import { useEffect, useState } from "react"
import Link from "next/link"
import { ArrowUpRight, X } from "lucide-react"
import { useLocale } from "@/lib/context"
import { money } from "@/lib/dashboard"
import { orderTime } from "@/lib/workbench"
import { adminOrderApi } from "@/services/api"
import { WorkbenchDialog } from "./workbench-dialog"
import { LedgerState, WorkbenchStatus } from "./workbench-orders"
import type { AdminOrderItem } from "@/types"
import type { WorkbenchOrder } from "@/types/workbench"
export function WorkbenchOrderDetail({ order, onClose }: { order: WorkbenchOrder; onClose: () => void }) {
  const { locale } = useLocale(), zh = locale === "zh"
  const [detail, setDetail] = useState<AdminOrderItem | null>(null), [error, setError] = useState(""), [revision, setRevision] = useState(0)
  useEffect(() => { let cancelled = false; setDetail(null); setError(""); adminOrderApi.getDetail(order.id).then(data => { if (!cancelled) setDetail(data) }).catch(err => { if (!cancelled) setError(err instanceof Error ? err.message : "Unable to load order") }); return () => { cancelled = true } }, [order.id, revision])
  return <WorkbenchDialog label={zh ? "订单详情" : "Order details"} onClose={onClose}><section className="wb-detail"><header><div><small>{zh ? "交易追查" : "Transaction details"}</small><h2>{zh ? "订单详情" : "Order details"}</h2></div><button type="button" aria-label={zh ? "关闭订单详情" : "Close order details"} onClick={onClose}><X size={20} /></button></header>
    <div className="wb-detail-amount"><strong>{money(order.actual_amount)}</strong><WorkbenchStatus status={order.status} /></div>
    <dl><div><dt>{zh ? "订单号" : "Order ID"}</dt><dd>{order.id}</dd></div><div><dt>{zh ? "渠道支付单号" : "Payment reference"}</dt><dd>{order.payment_reference || (zh ? "未记录" : "Not recorded")}</dd></div><div><dt>{zh ? "付款时间（上海）" : "Paid time (Shanghai)"}</dt><dd>{orderTime(order.paid_at, locale, true)}</dd></div></dl>
    <LedgerState loading={!detail && !error} error={error} empty={false} retry={() => setRevision(value => value + 1)} />
    {detail && <><div className="wb-detail-items">{detail.items?.map(item => <div key={item.id}><span><strong>{item.product_title}</strong><small>{item.spec_name} × {item.quantity}</small></span><b>{money(item.subtotal)}</b></div>)}</div><dl><div><dt>{zh ? "联系邮箱" : "Contact email"}</dt><dd>{detail.email || "—"}</dd></div></dl></>}
    <p className="wb-accounting-note">{zh ? "订单付款状态不代表渠道已结算；到账与手续费尚未核对。" : "Paid status does not confirm channel settlement. Receipts and fees have not been reconciled."}</p>
    <footer><Link className="wb-button is-primary" href={`/admin/orders?keyword=${encodeURIComponent(order.id)}`}>{zh ? "前往订单管理" : "Open order management"}<ArrowUpRight size={15} /></Link></footer>
  </section></WorkbenchDialog>
}
