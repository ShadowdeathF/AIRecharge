"use client"
import { useCallback, useEffect, useRef, useState, type CSSProperties } from "react"
import { detectArtworkBounds, type ArtworkBounds } from "@/lib/artwork-bounds"

const cache = new Map<string, ArtworkBounds | null>()
interface Artwork { src: string; width: number; height: number; bounds: ArtworkBounds | null }

export function ProductArtwork({ src, title, merchant, priority, contain = false }: { src: string; title: string; merchant: boolean; priority: boolean; contain?: boolean }) {
  const frame = useRef<HTMLSpanElement>(null), image = useRef<HTMLImageElement>(null)
  const [artwork, setArtwork] = useState<Artwork | null>(null)
  const [size, setSize] = useState({ width: 0, height: 0 })
  const inspect = useCallback((img: HTMLImageElement) => {
    if (!merchant || !img.naturalWidth || img.getAttribute("src") !== src) return
    if (!cache.has(src)) {
      let bounds: ArtworkBounds | null = null
      try {
        const canvas = document.createElement("canvas")
        const factor = 96 / Math.max(img.naturalWidth, img.naturalHeight)
        canvas.width = Math.max(1, Math.round(img.naturalWidth * factor))
        canvas.height = Math.max(1, Math.round(img.naturalHeight * factor))
        const context = canvas.getContext("2d", { willReadFrequently: true })
        if (context) { context.drawImage(img, 0, 0, canvas.width, canvas.height); bounds = detectArtworkBounds(context.getImageData(0, 0, canvas.width, canvas.height).data, canvas.width, canvas.height) }
      } catch { /* Cross-origin or unreadable images use the ordinary cover fallback. */ }
      if (cache.size >= 128) cache.clear()
      cache.set(src, bounds)
    }
    setArtwork({ src, width: img.naturalWidth, height: img.naturalHeight, bounds: cache.get(src) || null })
  }, [merchant, src])
  useEffect(() => { if (image.current?.complete) inspect(image.current) }, [inspect])
  const bounds = artwork?.src === src ? artwork.bounds : null
  useEffect(() => {
    if (!bounds || !frame.current) return
    const element = frame.current
    const measure = () => setSize({ width: element.clientWidth, height: element.clientHeight })
    measure(); const observer = new ResizeObserver(measure); observer.observe(element)
    return () => observer.disconnect()
  }, [bounds])
  let style: CSSProperties | undefined
  if (bounds && artwork && size.width && size.height) {
    const w = bounds.width * artwork.width, h = bounds.height * artwork.height
    const scale = contain ? Math.min(size.width / w, size.height / h) : Math.max(size.width / w, size.height / h)
    style = { position: "absolute", maxWidth: "none", width: artwork.width * scale, height: artwork.height * scale, left: (size.width - w * scale) / 2 - bounds.x * artwork.width * scale, top: (size.height - h * scale) / 2 - bounds.y * artwork.height * scale }
  }
  return <span className="store-artwork-frame" ref={frame}><img ref={image} src={src} alt={title} data-merchant-cover={merchant} data-artwork-cropped={Boolean(style) || undefined} style={style} loading={priority ? "eager" : "lazy"} onLoad={event => inspect(event.currentTarget)} /></span>
}
