"use client"

import { useEffect, useRef } from "react"
import { animateContent } from "@/lib/motion"

export function useContentMotion<T extends HTMLElement>(trigger: string) {
  const ref = useRef<T>(null)

  useEffect(() => {
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
  }, [trigger])

  return ref
}
