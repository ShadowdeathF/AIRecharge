"use client"
import { useEffect, useRef, type ReactNode } from "react"
let openDialogs = 0
let originalOverflow = ""
export function WorkbenchDialog({ label, onClose, children, drawer = false }: { label: string; onClose: () => void; children: ReactNode; drawer?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const element = ref.current
    element?.showModal()
    if (openDialogs === 0) originalOverflow = document.body.style.overflow
    openDialogs++
    document.body.style.overflow = "hidden"
    return () => { element?.close(); openDialogs--; if (openDialogs === 0) document.body.style.overflow = originalOverflow }
  }, [])
  return <dialog ref={ref} aria-label={label} className={`wb-dialog ${drawer ? "wb-day-dialog" : ""}`} onCancel={event => { event.preventDefault(); onClose() }} onClick={event => { if (event.target === ref.current) { const bounds = ref.current.getBoundingClientRect(); if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) onClose() } }}>{children}</dialog>
}
