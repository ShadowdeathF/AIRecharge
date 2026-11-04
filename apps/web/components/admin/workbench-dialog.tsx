"use client"
import { useEffect, useRef, type ReactNode } from "react"
import { motion, useIsPresent } from "motion/react"
import { useMotionPreference } from "@/hooks/use-motion-preference"
let openDialogs = 0
let originalOverflow = ""
export function WorkbenchDialog({ label, onClose, children, drawer = false, className = "" }: { label: string; onClose: () => void; children: ReactNode; drawer?: boolean; className?: string }) {
  const reduced = useMotionPreference()
  const present = useIsPresent()
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const element = ref.current
    const previousFocus = document.activeElement
    element?.showModal()
    if (openDialogs === 0) originalOverflow = document.body.style.overflow
    openDialogs++
    document.body.style.overflow = "hidden"
    return () => {
      element?.close()
      openDialogs--
      if (openDialogs === 0) document.body.style.overflow = originalOverflow
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus({ preventScroll: true })
    }
  }, [])
  return <motion.dialog
    ref={ref}
    aria-label={label}
    aria-hidden={!present}
    inert={!present}
    initial={reduced ? false : { opacity: 0, x: drawer ? 80 : 0, y: drawer ? 0 : 18, scale: drawer ? 1 : .97 }}
    animate={{ opacity: 1, x: 0, y: 0, scale: 1 }}
    exit={reduced ? { opacity: 0, transition: { duration: 0 } } : { opacity: 0, x: drawer ? 80 : 0, y: drawer ? 0 : 18, scale: drawer ? 1 : .97 }}
    className={`wb-dialog ${drawer ? "wb-day-dialog" : ""} ${className}`}
    onCancel={event => { event.preventDefault(); onClose() }}
    onClick={event => {
      if (event.target !== ref.current) return
      const bounds = ref.current.getBoundingClientRect()
      if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) onClose()
    }}
  >{children}</motion.dialog>
}
