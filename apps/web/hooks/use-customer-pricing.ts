"use client"
import { useEffect,useRef,useState } from "react"
import { useAuth } from "@/lib/context"
import { productApi } from "@/services/api"
import { loadCatalogProducts } from "@/lib/catalog-pagination"
import type { ProductCard,ProductDetail } from "@/types"

function usePrivatePrice<T>(fallback:T,load:()=>Promise<T>,identity:string){
  const {user,authLoaded}=useAuth(),owner=user?.id||"",[tick,setTick]=useState(0)
  const [state,setState]=useState<{owner:string;identity:string;value:T|null;error:string}>({owner:"",identity:"",value:null,error:""})
  const loader=useRef(load);loader.current=load
  useEffect(()=>{let active=true;setState({owner,identity,value:null,error:""});if(!owner)return()=>{active=false};loader.current().then(value=>{if(active)setState({owner,identity,value,error:""})}).catch(error=>{if(active)setState({owner,identity,value:null,error:error instanceof Error?error.message:"账户价格读取失败"})});return()=>{active=false}},[owner,identity,tick])
  const current=state.owner===owner&&state.identity===identity
  return{value:owner&&current&&state.value?state.value:fallback,error:owner&&current?state.error:"",ready:authLoaded&&(!owner||current&&state.value!==null),refresh:()=>setTick(t=>t+1)}
}
export function useCustomerCatalog(fallback:ProductCard[]){return usePrivatePrice(fallback,()=>loadCatalogProducts((page,size)=>productApi.getList({page,page_size:size})),"catalog")}
export function useCustomerProduct(fallback:ProductDetail){return usePrivatePrice(fallback,()=>productApi.getDetail(fallback.id),fallback.id)}
