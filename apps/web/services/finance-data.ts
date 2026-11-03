import { adminProductApi } from "./api"
import type { ProductDetail } from "@/types"
export async function loadFinanceProducts():Promise<ProductDetail[]>{
  const result:ProductDetail[]=[]
  for(let page=1;page<=100;page++){const response=await adminProductApi.getList({page,page_size:100});result.push(...response.list);if(result.length>=response.pagination.total||response.list.length===0)return result}
  throw new Error("商品数量过多，请缩小商品范围")
}
