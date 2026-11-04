"use client"

import { motion } from "motion/react"
import { useMotionPreference } from "@/hooks/use-motion-preference"
import { fluidSpring } from "@/lib/motion"

export function SelectionIndicator() {
  const reduced = useMotionPreference()
  return <motion.span
    aria-hidden="true"
    className="fluid-selection"
    layoutId={reduced ? undefined : "selection"}
    transition={reduced ? { duration: 0 } : fluidSpring}
    style={{ borderRadius: "inherit" }}
  />
}
