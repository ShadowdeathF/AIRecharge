import type { FinancePerson, OfflineInput } from "@/types/finance"

export function financeMoney(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === "") return "待补"
  const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(String(value))
  if (!match) return "—"
  return `${match[1]}¥${BigInt(match[2]).toLocaleString("en-US")}.${(match[3] || "").padEnd(2, "0").slice(0, 2)}`
}
export function cents(value: string | number): bigint {
  const text = String(value), match = /^(-?)(\d+)(?:\.(\d{1,2}))?$/.exec(text)
  if (!match) throw new Error("金额最多支持两位小数")
  return (BigInt(match[2]) * BigInt(100) + BigInt((match[3] || "").padEnd(2, "0"))) * (match[1] ? -BigInt(1) : BigInt(1))
}
export function decimal(value: bigint): string { return `${value < BigInt(0) ? "-" : ""}${(value < BigInt(0) ? -value : value) / BigInt(100)}.${String((value < BigInt(0) ? -value : value) % BigInt(100)).padStart(2, "0")}` }
export function difference(income: string | number, cost: string | number | null | undefined): string | null { return cost == null ? null : decimal(cents(income) - cents(cost)) }
export function newFinanceKey(): string { return globalThis.crypto?.randomUUID?.() || `finance-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}` }
export function quarterOf(date: string): string { return `${date.slice(0, 4)}-Q${Math.floor((Number(date.slice(5, 7)) - 1) / 3) + 1}` }
export function referralLabel(id: string | null | undefined, people: Pick<FinancePerson,"id"|"name">[]): string { return id ? people.find(p => p.id === id)?.name || "已存档人员" : "无推荐人 · 季度分红池" }
export const sourceNames: Record<string,string> = { RETAIL_ONLINE:"散客线上",COMPANY_ONLINE:"企业线上",COMPANY_OFFLINE:"企业线下" }
export const documentNames: Record<string,string> = { PROFIT:"利润结算",QUARTER:"季度分红",ADJUSTMENT:"利润调整",QUARTER_ADJUSTMENT:"季度调整" }
export const roleNames: Record<string,string> = { TECH:"技术总监",BOSS:"总经理",FINANCE:"财务总监",OTHER:"其他人员" }
export const accountNames: Record<string,string> = { PERSONAL_PAYABLE:"人员应付",OPERATING_RESERVE:"经营预留",DIVIDEND_RESERVE:"季度分红预留" }

export function parseFinanceCsv(input: string): string[][] {
  const text = input.replace(/^\uFEFF/, ""), rows: string[][] = []
  let row: string[] = [], cell = "", quoted = false, afterQuote = false
  for (let i=0;i<text.length;i++) {
    const ch=text[i]
    if (quoted) { if(ch==='"'){if(text[i+1]==='"'){cell+='"';i++}else{quoted=false;afterQuote=true}}else cell+=ch;continue }
    if(ch==='"'){if(cell!==""||afterQuote)throw new Error("CSV引号位置错误");quoted=true;continue}
    if(ch===','){row.push(cell);cell="";afterQuote=false;continue}
    if(ch==='\r'||ch==='\n'){if(ch==='\r'&&text[i+1]==='\n')i++;row.push(cell);if(row.some(v=>v.trim()))rows.push(row);row=[];cell="";afterQuote=false;continue}
    if(afterQuote&&!/\s/.test(ch))throw new Error("CSV引号后存在无效内容")
    if(!afterQuote)cell+=ch
    if(rows.length>5000)throw new Error("一次最多导入5000行商品")
  }
  if(quoted)throw new Error("CSV引号未闭合")
  row.push(cell);if(row.some(v=>v.trim()))rows.push(row)
  return rows
}
export function offlineImport(csv: string): OfflineInput[] {
  const rows=parseFinanceCsv(csv)
  if(rows.length<2)throw new Error("CSV没有交易记录")
  const headers=rows[0].map(s=>s.trim()),required=["company_id","external_reference","business_date","product_id","quantity","unit_price"]
  if(new Set(headers).size!==headers.length)throw new Error("CSV列名重复")
  for(const name of required)if(!headers.includes(name))throw new Error(`缺少必填列：${name}`)
  const result=new Map<string,OfflineInput>(),uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
  for(let i=1;i<rows.length;i++){
    if(rows[i].length!==headers.length)throw new Error(`第${i+1}行列数与表头不同`)
    const value=Object.fromEntries(headers.map((h,j)=>[h,rows[i][j].trim()]))
    if(!uuid.test(value.company_id)||(value.source_order_id?!uuid.test(value.source_order_id):!uuid.test(value.product_id))||(value.spec_id&&!uuid.test(value.spec_id))||(value.referrer_id&&!uuid.test(value.referrer_id)))throw new Error(`第${i+1}行企业、商品、规格或人员ID无效`)
    if(!value.external_reference||value.external_reference.length>120)throw new Error(`第${i+1}行外部单号无效`)
    const date=new Date(value.business_date+"T00:00:00Z")
    if(!/^\d{4}-\d{2}-\d{2}$/.test(value.business_date)||!Number.isFinite(date.valueOf())||date.toISOString().slice(0,10)!==value.business_date)throw new Error(`第${i+1}行业务日期无效`)
    if(!value.source_order_id&&(!/^\d{1,6}$/.test(value.quantity)||Number(value.quantity)<1||cents(value.unit_price)<BigInt(0)))throw new Error(`第${i+1}行数量或单价无效`)
    const key=value.company_id+":"+value.external_reference.toLowerCase(),existing=result.get(key)
    if(existing&&(existing.source_order_id!==(value.source_order_id||undefined)||existing.business_date!==value.business_date||existing.referrer_id!==(value.referrer_id||null)))throw new Error(`第${i+1}行与同一订单的日期或推荐人不同`)
    const transaction=existing||{source_order_id:value.source_order_id||undefined,company_id:value.company_id,external_reference:value.external_reference,business_date:value.business_date,referrer_id:value.referrer_id||null,note:value.note||"",idempotency_key:newFinanceKey(),items:[]}
    if(!value.source_order_id)transaction.items.push({product_id:value.product_id,spec_id:value.spec_id||null,quantity:Number(value.quantity),unit_price:value.unit_price,price_reason:value.price_reason||""})
    result.set(key,transaction)
    if(result.size>1000)throw new Error("一次最多导入1000笔交易")
  }
  return [...result.values()]
}
export function saveFinanceFile(content: string | Blob, filename: string) {
  const blob=typeof content==="string"?new Blob([content],{type:"text/csv;charset=utf-8"}):content
  const url=URL.createObjectURL(blob),link=document.createElement("a");link.href=url;link.download=filename;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000)
}
