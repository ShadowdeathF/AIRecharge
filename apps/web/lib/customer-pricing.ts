import type { ProductDetail,ProductSpec } from "@/types"
export function customerUnitPrice(product:ProductDetail,spec:ProductSpec|null,quantity:number):number {
  const base=spec?.price??product.base_price
  if((spec?.price_source||product.price_source)==="COMPANY"||!product.wholesale_enabled)return base
  const rules=product.wholesale_rules.filter(rule=>(rule.spec_id||null)===(spec?.id||null)&&quantity>=rule.min_quantity)
  return rules.sort((a,b)=>b.min_quantity-a.min_quantity)[0]?.unit_price??base
}
