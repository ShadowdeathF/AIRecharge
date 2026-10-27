"use client"

import { useState, useEffect } from "react"
import { cn } from "@/lib/utils"
import { useLocale } from "@/lib/context"
import { ArrowUp } from "lucide-react"

export function ScrollToTop() {
  const { t } = useLocale()
  const [show, setShow] = useState(false)

  useEffect(() => {
    const handleScroll = () => setShow(window.scrollY > 400)
    window.addEventListener("scroll", handleScroll, { passive: true })
    return () => window.removeEventListener("scroll", handleScroll)
  }, [])

  return (
    <button
      onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}
      className={cn(
        "fixed bottom-24 right-4 z-40 flex h-11 w-11 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg transition-all duration-300 hover:scale-105 active:scale-95 min-[801px]:bottom-8 min-[801px]:right-8",
        show ? "translate-y-0 opacity-100" : "pointer-events-none translate-y-4 opacity-0"
      )}
      aria-label={t("common.backToTop")}
    >
      <ArrowUp className="h-5 w-5" />
    </button>
  )
}
