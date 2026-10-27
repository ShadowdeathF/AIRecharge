"use client"
import { useState } from "react"
import Link from "next/link"
import { ArrowRight, X, ClipboardList } from "lucide-react"
import { useLocale } from "@/lib/context"
import { OrderStatusBadge } from "@/components/shared/order-status-badge"
import { Modal } from "@/components/ui/modal"
import { money } from "@/lib/dashboard"
import type { AdminOrderItem } from "@/types"
export function RecentOrders({ orders }: { orders: AdminOrderItem[] }) {
  const { locale } = useLocale(); const zh=locale==="zh"
  const [selected,setSelected]=useState<AdminOrderItem|null>(null)
  const time=(value:string)=>new Date(/(?:Z|[+-]\d{2}:?\d{2})$/.test(value)?value:value.replace(" ","T")+"+08:00").toLocaleString(zh?"zh-CN":"en-GB",{timeZone:"Asia/Shanghai",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",hour12:false})
  const status=(order:AdminOrderItem)=>order.status==="PAID"?<span className="analytics-paid-badge">{zh?"已付款待发货":"Paid · awaiting delivery"}</span>:<OrderStatusBadge status={order.status}/>
  return <section className="admin-panel analytics-recent-orders"><div className="admin-panel-heading"><h2>{zh?"最近订单":"Recent orders"}</h2><Link href="/admin/orders">{zh?"查看全部":"View all"}<ArrowRight size={13}/></Link></div>
    {orders.length===0?<div className="admin-empty-compact"><ClipboardList size={25}/><p>{zh?"暂无订单":"No orders yet"}</p></div>:<>
      <div className="analytics-orders-table"><table><thead><tr>{(zh?["订单号","商品","金额","状态","下单时间","操作"]:["Order","Product","Amount","Status","Placed","Action"]).map(label=><th key={label}>{label}</th>)}</tr></thead><tbody>{orders.map(order=><tr key={order.id}><td className="admin-order-id" title={order.id}>{order.id.slice(0,8)}…{order.id.slice(-4)}</td><td><span className="admin-order-product" title={order.items?.map(item=>item.product_title).join("、")}>{order.items?.[0]?.product_title || "—"}{order.items?.length>1?` +${order.items.length-1}`:""}</span></td><td className="admin-order-amount">{money(order.actual_amount)}</td><td>{status(order)}</td><td className="admin-order-time">{time(order.created_at)}</td><td><button type="button" onClick={()=>setSelected(order)} aria-label={`${zh?"查看订单":"View order"} ${order.id}`}>{zh?"查看":"View"}</button></td></tr>)}</tbody></table></div>
      <div className="analytics-orders-mobile">{orders.map(order=><button type="button" key={order.id} onClick={()=>setSelected(order)}><span><strong>{order.items?.[0]?.product_title || order.id.slice(0,8)}</strong><small>{time(order.created_at)}</small></span><span><b>{money(order.actual_amount)}</b>{status(order)}</span></button>)}</div>
    </>}
    <Modal open={!!selected} onClose={()=>setSelected(null)} className="max-w-xl">{selected&&<section className="admin-order-dialog" role="dialog" aria-modal="true" aria-labelledby="order-detail-title"><header><h2 id="order-detail-title">{zh?"订单详情":"Order details"}</h2><button type="button" aria-label={zh?"关闭订单详情":"Close order details"} onClick={()=>setSelected(null)}><X size={19}/></button></header><dl><div><dt>{zh?"订单号":"Order ID"}</dt><dd>{selected.id}</dd></div><div><dt>{zh?"订单状态":"Status"}</dt><dd>{status(selected)}</dd></div><div><dt>{zh?"订单金额":"Amount"}</dt><dd>{money(selected.actual_amount)}</dd></div><div><dt>{zh?"联系邮箱":"Email"}</dt><dd>{selected.email}</dd></div><div><dt>{zh?"下单时间":"Placed"}</dt><dd>{time(selected.created_at)}</dd></div></dl><div className="admin-order-dialog-items">{selected.items?.map(item=><p key={item.id}><strong>{item.product_title}</strong><span>{item.spec_name} ×{item.quantity}</span><b>{money(item.subtotal)}</b></p>)}</div><Link className="admin-primary-button" href="/admin/orders">{zh?"前往订单管理":"Open order management"}<ArrowRight size={15}/></Link></section>}</Modal>
  </section>
}
