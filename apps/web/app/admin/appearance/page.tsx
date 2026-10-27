"use client"
import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import Link from "next/link"
import { Check, Upload, Save, ExternalLink, ImagePlus, Monitor, Smartphone, Loader2 } from "lucide-react"
import { toast } from "sonner"
import { adminConfigApi, adminProductApi } from "@/services/api"
import { useLocale, useSiteConfig } from "@/lib/context"
import { COLOR_SCHEMES, getSiteColor, isSafeSiteUrl } from "@/lib/appearance"
const KEYS=["store_theme_color","hero_enabled","hero_image_url","hero_mobile_image_url","hero_link_url","site_slogan","site_description"] as const
const defaults: Record<string,string>={store_theme_color:"blue",hero_enabled:"true",hero_image_url:"/images/store/hero-key.webp",hero_mobile_image_url:"",hero_link_url:"",site_slogan:"",site_description:""}
export default function AdminAppearancePage(){
  const {locale}=useLocale();const zh=locale==="zh"
  const {refreshConfig}=useSiteConfig();const router=useRouter()
  const [values,setValues]=useState(defaults),[loading,setLoading]=useState(true),[saving,setSaving]=useState(false),[error,setError]=useState("")
  const [uploading,setUploading]=useState<string|null>(null),[device,setDevice]=useState<"desktop"|"mobile">("desktop"),[imageError,setImageError]=useState(false)
  const [revision,setRevision]=useState(0),[dirty,setDirty]=useState(false)
  useEffect(()=>{let cancelled=false;setLoading(true);setError("");adminConfigApi.get().then(rows=>{if(cancelled)return;const next={...defaults};for(const row of rows)if(KEYS.includes(row.config_key as typeof KEYS[number]))next[row.config_key]=row.config_value;setValues(next);setDirty(false)}).catch(()=>{if(!cancelled)setError("外观配置加载失败，请重试。 / Unable to load appearance settings.")}).finally(()=>{if(!cancelled)setLoading(false)});return()=>{cancelled=true}},[revision])
  const set=(key:string,value:string)=>{setValues(previous=>({...previous,[key]:value}));setDirty(true)}
  const image=device==="mobile"&&values.hero_mobile_image_url?values.hero_mobile_image_url:values.hero_image_url||defaults.hero_image_url
  useEffect(()=>setImageError(false),[image])
  const upload=async(key:string,file?:File)=>{
    if(!file)return
    if(!["image/jpeg","image/png","image/webp","image/gif"].includes(file.type)||file.size>10*1024*1024){toast.error(zh?"请上传不超过10MB的 JPG、PNG、WebP 或 GIF 图片":"Use JPG, PNG, WebP or GIF up to 10 MB");return}
    setUploading(key)
    try{const result=await adminProductApi.uploadImage(file);set(key,result.url);toast.success(zh?"上传完成，保存后生效":"Uploaded. Save to apply.")}catch(err){toast.error(err instanceof Error?err.message:"Upload failed")}finally{setUploading(null)}
  }
  const save=async(e:React.FormEvent)=>{
    e.preventDefault()
    if(["hero_image_url","hero_mobile_image_url","hero_link_url"].some(key=>!isSafeSiteUrl(values[key]))){toast.error(zh?"图片或链接地址无效，仅支持站内路径或 HTTP/HTTPS":"Use a site path or HTTP/HTTPS URL");return}
    setSaving(true)
    try{await adminConfigApi.update({configs:KEYS.map(key=>({config_key:key,config_value:values[key]}))});setDirty(false);await refreshConfig();router.refresh();toast.success(zh?"已保存，商城刷新后使用新的站点外观":"Saved. Refresh the store to see changes.")}catch(err){toast.error(err instanceof Error?err.message:"Save failed")}finally{setSaving(false)}
  }
  const accent=COLOR_SCHEMES.find(s=>s.key===getSiteColor(values.store_theme_color))!.color
  return <div className="appearance-page"><div className="analytics-page-heading"><div><h1>{zh?"站点外观":"Site appearance"}</h1><p>{zh?"统一管理商城横幅与主题配色，访客只选择语言和明暗模式。":"Manage storefront banners and brand color. Visitors choose language and light/dark mode."}</p></div><Link className="admin-text-button" href="/" target="_blank" rel="noopener noreferrer">{zh?"查看商城":"View store"}<ExternalLink size={14}/></Link></div>
    {loading?<div className="admin-loading">{zh?"正在加载外观配置…":"Loading appearance…"}</div>:error?<div className="admin-error" role="alert">{error}<button type="button" onClick={()=>setRevision(v=>v+1)}>{zh?"重试":"Retry"}</button></div>:<form onSubmit={save} className="appearance-layout">
      <fieldset className="appearance-fields" disabled={saving||!!uploading}>
        <section className="admin-panel"><div className="admin-panel-heading"><h2>{zh?"商城主题配色":"Store brand color"}</h2></div><p className="appearance-help">{zh?"保存后统一应用于所有访客，旧浏览器配色选择不再生效。":"Applies to all visitors. Old browser palette preferences are ignored."}</p><div className="appearance-color-grid" role="group" aria-label={zh?"主题配色":"Brand color"}>{COLOR_SCHEMES.map(scheme=><button key={scheme.key} type="button" aria-pressed={values.store_theme_color===scheme.key} onClick={()=>set("store_theme_color",scheme.key)}><span style={{backgroundColor:scheme.color}}>{values.store_theme_color===scheme.key&&<Check size={17} color="white"/>}</span>{zh?scheme.label:scheme.key}</button>)}</div></section>
        <section className="admin-panel"><div className="admin-panel-heading"><h2>{zh?"首页横幅":"Homepage banner"}</h2><label className="appearance-toggle"><input type="checkbox" aria-label={zh?"显示首页横幅":"Show homepage banner"} checked={values.hero_enabled==="true"} onChange={e=>set("hero_enabled",String(e.target.checked))}/>{zh?"显示横幅":"Show banner"}</label></div>
          <div className="appearance-form-fields">
            {[{key:"hero_image_url",label:zh?"电脑端横幅图":"Desktop banner",help:zh?"推荐宽幅图片，例如1600×400；支持本地上传或图片地址。":"Wide image recommended, e.g. 1600×400."},{key:"hero_mobile_image_url",label:zh?"手机端横幅图":"Mobile banner",help:zh?"可选；留空时使用电脑端图片，建议把主体放在右侧。":"Optional. Falls back to the desktop image."}].map(field=><div className="appearance-field" key={field.key}><label htmlFor={field.key}>{field.label}</label><div className="appearance-upload-row"><input id={field.key} value={values[field.key]} inputMode="url" placeholder={field.key==="hero_mobile_image_url"?(zh?"留空则使用电脑图":"Use desktop image"):"/api/uploads/banner.webp"} onChange={e=>set(field.key,e.target.value)}/><label className="admin-secondary-button appearance-upload">{uploading===field.key?<Loader2 size={15} className="animate-spin"/>:<Upload size={15}/>}<span>{zh?"上传":"Upload"}</span><input type="file" aria-label={`${zh?"上传":"Upload"}${field.label}`} accept="image/jpeg,image/png,image/webp,image/gif" disabled={!!uploading} onChange={e=>{void upload(field.key,e.target.files?.[0]);e.target.value=""}}/></label></div><p>{field.help}</p></div>)}
            <div className="appearance-field"><label htmlFor="hero-title">{zh?"横幅标题":"Banner title"}</label><input id="hero-title" value={values.site_slogan} onChange={e=>set("site_slogan",e.target.value)} maxLength={160}/><p>{zh?"与网站设置中的网站标语同步。":"Shares the site's slogan setting."}</p></div>
            <div className="appearance-field"><label htmlFor="hero-description">{zh?"横幅说明":"Banner description"}</label><textarea id="hero-description" value={values.site_description} onChange={e=>set("site_description",e.target.value)} rows={2}/></div>
            <div className="appearance-field"><label htmlFor="hero-link">{zh?"点击跳转链接":"Banner destination"}</label><input id="hero-link" inputMode="url" value={values.hero_link_url} onChange={e=>set("hero_link_url",e.target.value)} placeholder={zh?"可选，例如 /product/商品ID":"Optional, e.g. /product/product-id"}/><p>{zh?"留空则横幅不可点击；支持站内路径或 HTTP/HTTPS。":"Leave empty for a non-clickable banner. Site paths and HTTP/HTTPS supported."}</p></div>
          </div>
        </section>
        <div className="appearance-save-bar"><span>{dirty?(zh?"有未保存的更改":"Unsaved changes"):(zh?"配置已同步":"Settings synchronized")}</span><button type="submit" className="admin-primary-button" disabled={saving||!!uploading||!dirty}>{saving?<Loader2 size={16} className="animate-spin"/>:<Save size={16}/>} {saving?(zh?"正在保存":"Saving"):(zh?"保存站点外观":"Save appearance")}</button></div>
      </fieldset>
      <aside className="appearance-preview admin-panel"><div className="admin-panel-heading"><h2>{zh?"实时预览":"Preview"}</h2><div className="appearance-preview-switch"><button type="button" aria-label={zh?"电脑预览":"Desktop preview"} aria-pressed={device==="desktop"} onClick={()=>setDevice("desktop")}><Monitor size={17}/></button><button type="button" aria-label={zh?"手机预览":"Mobile preview"} aria-pressed={device==="mobile"} onClick={()=>setDevice("mobile")}><Smartphone size={17}/></button></div></div><p className="appearance-help">{zh?"预览不会修改已发布外观，点击保存后生效。":"Preview does not change the live store until saved."}</p>
        <div className={`appearance-mini-store ${device==="mobile"?"is-mobile":""}`} style={{"--preview-accent":accent} as React.CSSProperties}><header><strong>AI Recharge</strong><span>{zh?"商城":"Store"}</span></header>{values.hero_enabled==="true"?<div className="appearance-mini-hero">{isSafeSiteUrl(image)&&!imageError?<img src={image} alt="" onError={()=>setImageError(true)}/>:<span className="appearance-image-error"><ImagePlus size={23}/>{zh?"图片无法预览，请检查地址":"Unable to preview image"}</span>}<div><h3>{values.site_slogan|| (zh?"你的下一份 AI 生产力。":"Your next AI advantage.")}</h3><p>{values.site_description}</p></div></div>:<div className="appearance-mini-disabled">{zh?"首页横幅已关闭":"Banner hidden"}</div>}<div className="appearance-mini-controls"><span>{zh?"全部商品":"All products"}</span><button type="button" tabIndex={-1}>{zh?"查看详情":"View details"}</button></div></div>
        <p className="appearance-help">{zh?"网站 Logo 可在":"Manage the site logo in "}<Link href="/admin/site-config">{zh?"网站设置":"Site settings"}</Link>{zh?"中修改。":"."}</p>
      </aside>
    </form>}
  </div>
}
