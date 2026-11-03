"use client"
import type { ProductDetail } from "@/types"
import { FinanceField } from "./ui"
export function FinanceProductSelector({products,productId,specId,onChange}:{products:ProductDetail[];productId:string;specId:string;onChange:(product:string,spec:string)=>void}){
  const product=products.find(p=>p.id===productId)
  return <><FinanceField label="商品"><select required value={productId} onChange={event=>{const selected=products.find(p=>p.id===event.target.value);onChange(event.target.value,selected?.spec_enabled?selected.specs?.[0]?.id||"":"")}}><option value="">请选择商品</option>{products.map(p=><option key={p.id} value={p.id}>{p.title}</option>)}</select></FinanceField><FinanceField label="规格"><select value={specId} required={Boolean(product?.spec_enabled)} disabled={!product?.spec_enabled} onChange={event=>onChange(productId,event.target.value)}>{!product?.spec_enabled?<option value="">默认规格</option>:<><option value="">请选择规格</option>{product.specs?.map(spec=><option key={spec.id} value={spec.id}>{spec.name}</option>)}</>}</select></FinanceField></>
}
