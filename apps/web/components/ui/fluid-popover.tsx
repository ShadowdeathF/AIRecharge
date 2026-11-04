"use client"

import { useCallback, useLayoutEffect, useRef, useState, type ReactNode, type RefObject } from "react"
import { createPortal } from "react-dom"
import { AnimatePresence, motion, useIsPresent } from "motion/react"
import { useMotionPreference } from "@/hooks/use-motion-preference"
import { fluidSpring } from "@/lib/motion"

interface FluidPopoverProps {
  open: boolean
  surfaceId: string
  anchor: RefObject<HTMLElement | null>
  label: string
  onClose: () => void
  className?: string
  width?: number
  children: ReactNode
}

export function FluidPopover({ open, ...props }: FluidPopoverProps) {
  return <AnimatePresence initial={false}>{open && <PopoverSurface {...props} />}</AnimatePresence>
}

function PopoverSurface({ surfaceId, anchor, label, onClose, className = "", width = 282, children }: Omit<FluidPopoverProps, "open">) {
  const present = useIsPresent()
  const reduced = useMotionPreference()
  const ref = useRef<HTMLElement>(null)
  useLayoutEffect(() => {
    if (present) ref.current?.querySelector<HTMLElement>('button:not([disabled]), a[href]')?.focus({ preventScroll: true })
  }, [present])
  const measure = useCallback(() => {
    const bounds = anchor.current?.getBoundingClientRect()
    const panelWidth = Math.min(width, window.innerWidth - 24)
    return { width: panelWidth, left: Math.max(12, Math.min((bounds?.right || panelWidth) - panelWidth, window.innerWidth - panelWidth - 12)), top: (bounds?.bottom || 60) + 12 }
  }, [anchor, width])
  const [position, setPosition] = useState(measure)
  useLayoutEffect(() => {
    const update = () => setPosition(measure())
    window.addEventListener("resize", update)
    window.addEventListener("scroll", update, true)
    return () => {
      window.removeEventListener("resize", update)
      window.removeEventListener("scroll", update, true)
    }
  }, [measure])
  return createPortal(<motion.section
    ref={ref}
    className={`store-popover fluid-popover ${className}`}
    aria-label={label}
    aria-hidden={!present}
    inert={!present}
    style={{ position: "fixed", right: "auto", ...position }}
    onKeyDown={event => {
      if (event.key !== "Tab") return
      const controls = ref.current?.querySelectorAll<HTMLElement>('button:not([disabled]), a[href]')
      const edge = event.shiftKey ? controls?.[0] : controls?.[controls.length - 1]
      if (document.activeElement !== edge) return
      if (event.shiftKey) event.preventDefault()
      anchor.current?.focus({ preventScroll: true })
      onClose()
    }}
    initial={{ opacity: 1 }}
    exit={{ opacity: 0 }}
    transition={{ duration: reduced ? 0 : .12, delay: reduced ? 0 : .2 }}
  >
    <motion.span
      aria-hidden="true"
      className="fluid-menu-surface"
      layoutId={reduced ? undefined : surfaceId}
      style={{ borderRadius: 22 }}
      transition={reduced ? { duration: 0 } : fluidSpring}
    />
    <motion.div className="fluid-menu-content"
      style={{ maxHeight: `calc(100dvh - ${position.top + 52}px)`, overflowY: "auto" }}
      initial={reduced ? false : { opacity: 0, y: 8, filter: "blur(4px)" }}
      animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
      exit={{ opacity: 0, y: 4, filter: "blur(3px)", transition: { duration: reduced ? 0 : .08 } }}
      transition={{ duration: reduced ? 0 : .18, delay: reduced ? 0 : .09 }}
    >{children}</motion.div>
  </motion.section>, document.body)
}
