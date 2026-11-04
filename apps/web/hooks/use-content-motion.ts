"use client"

import { useEffect, useRef, type RefObject } from "react"
import { animateContent } from "@/lib/motion"

interface ContentMotionOptions<T extends HTMLElement> {
  native?: boolean
  historyPath?: RefObject<string | null> | null
  target?: RefObject<T | null>
}

export function useContentMotion<T extends HTMLElement>(trigger: string, options: ContentMotionOptions<T> = {}) {
  const ownRef = useRef<T>(null)
  const { native = false, historyPath, target } = options
  const ref = target || ownRef

  useEffect(() => {
    // Next restores cached history outside React's transition update.
    const historyTraversal = historyPath?.current === trigger
    if (historyTraversal) historyPath.current = null
    if (native && !historyTraversal && typeof document.startViewTransition === "function") return
    const preference = window.matchMedia("(prefers-reduced-motion: reduce)")
    const animation = animateContent(ref.current, preference.matches)
    const handlePreference = () => {
      if (preference.matches) animation?.cancel()
    }
    preference.addEventListener("change", handlePreference)
    return () => {
      preference.removeEventListener("change", handlePreference)
      animation?.cancel()
    }
  }, [trigger, native, historyPath, ref])

  return ref
}
