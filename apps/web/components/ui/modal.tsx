"use client"

import { useEffect, useId, useRef, type ReactNode } from "react"
import { cn } from "@/lib/utils"
import { containDialogFocus, lockPageScroll } from "@/lib/dialog-focus"

interface ModalProps {
  open: boolean
  onClose: () => void
  children: ReactNode
  className?: string
}

/**
 * 通用弹窗组件。
 * - 只有 mousedown + mouseup 都发生在背景蒙层上才会关闭（防止拖拽误关）
 * - 支持 ESC 键关闭
 */
export function Modal({ open, onClose, children, className }: ModalProps) {
  const mouseDownOnBackdrop = useRef(false)
  const panel = useRef<HTMLDivElement>(null)
  const titleId = useId()

  useEffect(() => {
    const element = panel.current
    if (!open || !element) return
    const heading = element.querySelector<HTMLElement>("h1, h2, h3")
    if (heading) {
      if (!heading.id) heading.id = titleId
      element.setAttribute("aria-labelledby", heading.id)
    } else {
      element.setAttribute("aria-label", document.documentElement.lang === "en" ? "Dialog" : "对话框")
    }
    const releaseScroll = lockPageScroll()
    const releaseFocus = containDialogFocus(element)
    return () => { releaseFocus(); releaseScroll() }
  }, [open, titleId])

  useEffect(() => {
    if (!open) return
    const handleEsc = (e: KeyboardEvent) => {
      if (document.activeElement?.closest("dialog[open]")) return
      const scopes = document.querySelectorAll('[data-modal-focus="true"]')
      if (e.key === "Escape" && scopes[scopes.length - 1] === panel.current) {
        e.stopImmediatePropagation()
        onClose()
      }
    }
    document.addEventListener("keydown", handleEsc)
    return () => document.removeEventListener("keydown", handleEsc)
  }, [open, onClose])

  if (!open) return null

  return (
    <div
      className="ui-modal-overlay fixed inset-0 z-50 overflow-y-auto bg-black/50"
      onMouseDown={(e) => {
        mouseDownOnBackdrop.current = e.target === e.currentTarget || (e.target as HTMLElement).dataset?.backdrop === "true"
      }}
      onClick={(e) => {
        const target = e.target as HTMLElement
        const isBackdrop = target === e.currentTarget || target.dataset?.backdrop === "true"
        if (isBackdrop && mouseDownOnBackdrop.current) {
          onClose()
        }
        mouseDownOnBackdrop.current = false
      }}
    >
      <div className="flex min-h-full items-center justify-center p-4" data-backdrop="true">
        <div
          ref={panel}
          role="dialog"
          aria-modal="true"
          tabIndex={-1}
          className={cn(
            "ui-modal-panel w-full rounded-xl border border-border bg-card shadow-xl",
            className
          )}
        >
          {children}
        </div>
      </div>
    </div>
  )
}
