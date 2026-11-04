"use client"

import { useSyncExternalStore } from "react"
import { getReducedMotion, subscribeReducedMotion } from "@/lib/motion"

export function useMotionPreference() {
  return useSyncExternalStore(subscribeReducedMotion, getReducedMotion, () => false)
}
