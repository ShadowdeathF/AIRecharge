"use client"
import { useEffect,useRef,useState,type ReactNode,type ButtonHTMLAttributes } from "react"
import { Loader2,X,ChevronLeft,ChevronRight,Receipt } from "lucide-react"
import { WorkbenchDialog } from "../workbench-dialog"
import { financeMoney } from "@/lib/finance"
import { toast } from "sonner"

export function useFinanceData<T>(load:()=>Promise<T>,dependencies:unknown[]=[]){
  const [data,setData]=useState<T|null>(null),[loading,setLoading]=useState(true),[error,setError]=useState(""),[tick,setTick]=useState(0)
  const loader=useRef(load);loader.current=load;const signature=JSON.stringify(dependencies)
  useEffect(()=>{let active=true;setLoading(true);setError("");setData(null);loader.current().then(value=>{if(active)setData(value)}).catch(e=>{if(active)setError(e instanceof Error?e.message:"读取失败")}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[signature,tick])
  return{data,loading,error,refresh:()=>setTick(t=>t+1)}
}
export function useFinanceAction(){
  const [busy,setBusy]=useState(false)
  async function run<T>(operation:()=>Promise<T>,success:string,onDone?:(value:T)=>void){if(busy)return;setBusy(true);try{const value=await operation();if(success)toast.success(success);onDone?.(value);return value}catch(e){toast.error(e instanceof Error?e.message:"操作失败，请重试")}finally{setBusy(false)}}
  return{busy,run}
}
export function FinanceButton({children,primary=false,danger=false,busy=false,className="",...props}:ButtonHTMLAttributes<HTMLButtonElement>&{primary?:boolean;danger?:boolean;busy?:boolean}){return <button type="button" {...props} disabled={props.disabled||busy} className={`fn-button ${primary?"is-primary":""} ${danger?"is-danger":""} ${className}`}>{busy&&<Loader2 size={15} className="animate-spin" aria-hidden="true"/>}{children}</button>}
export function FinanceField({label,hint,children,wide=false}:{label:string;hint?:string;children:ReactNode;wide?:boolean}){return <label className={`fn-field ${wide?"is-wide":""}`}><span>{label}</span>{children}{hint&&<small>{hint}</small>}</label>}
export function FinanceMoney({value}:{value:string|number|null|undefined}){return <span className={`fn-money ${value==null?"is-pending":String(value).startsWith("-")?"is-negative":""}`}>{financeMoney(value)}</span>}
export function FinanceMetric({label,value,hint}:{label:string;value:ReactNode;hint?:string}){return <article className="fn-metric"><span>{label}</span><strong>{value}</strong>{hint&&<small>{hint}</small>}</article>}
export function FinanceModal({title,description,onClose,children,drawer=false}:{title:string;description?:string;onClose:()=>void;children:ReactNode;drawer?:boolean}){return <WorkbenchDialog label={title} onClose={onClose} drawer={drawer} className={`finance-dialog ${drawer?"is-drawer":""}`}><header className="fn-modal-header"><div><h2>{title}</h2>{description&&<p>{description}</p>}</div><button type="button" aria-label="关闭" onClick={onClose}><X size={19}/></button></header><div className="fn-modal-content">{children}</div></WorkbenchDialog>}
export function FinanceState({loading,error,empty=false,retry}:{loading:boolean;error?:string;empty?:boolean;retry?:()=>void}){if(loading)return <div className="fn-state" role="status"><Loader2 className="animate-spin" size={23}/><p>正在读取…</p></div>;if(error)return <div className="fn-state" role="alert"><p>{error}</p>{retry&&<FinanceButton onClick={retry}>重试</FinanceButton>}</div>;if(empty)return <div className="fn-state"><Receipt size={28}/><p>暂无符合条件的记录</p><small>可调整筛选，或新增业务记录。</small></div>;return null}
export function FinanceTabs({tabs,value,onChange}:{tabs:{key:string;label:string}[];value:string;onChange:(key:string)=>void}){return <div className="fn-tabs" role="tablist">{tabs.map(tab=><button type="button" role="tab" aria-selected={value===tab.key} key={tab.key} onClick={()=>onChange(tab.key)}>{tab.label}</button>)}</div>}
export function FinancePager({page,total,size=20,onPage}:{page:number;total:number;size?:number;onPage:(page:number)=>void}){const pages=Math.max(1,Math.ceil(total/size));return <footer className="fn-pager"><span>共 {total} 条</span><div><button type="button" aria-label="上一页" disabled={page<=1} onClick={()=>onPage(page-1)}><ChevronLeft size={17}/></button><span>{page} / {pages}</span><button type="button" aria-label="下一页" disabled={page>=pages} onClick={()=>onPage(page+1)}><ChevronRight size={17}/></button></div></footer>}
export function FinanceBadge({children,tone="normal"}:{children:ReactNode;tone?:"normal"|"good"|"warn"|"bad"}){return <span className={`fn-badge is-${tone}`}>{children}</span>}
