"use client"

import { createContext, Suspense, useContext, useEffect, useRef, ViewTransition, type ReactNode, type RefObject } from "react"
import type {} from "react/canary"
import { usePathname, useSearchParams } from "next/navigation"
import { useContentMotion } from "@/hooks/use-content-motion"

const HistoryMotionContext = createContext<RefObject<string | null> | null>(null)

export function RouteFrame({ children }: { children: ReactNode }) {
  const historyPath = useRef<string | null>(null)
  useEffect(() => {
    const rememberTraversal = () => {
      const query = new URLSearchParams(window.location.search).toString()
      historyPath.current = window.location.pathname + (query ? `?${query}` : "")
    }
    window.addEventListener("popstate", rememberTraversal)
    return () => window.removeEventListener("popstate", rememberTraversal)
  }, [])

  return <HistoryMotionContext.Provider value={historyPath}>
    <ViewTransition default="route-frame"><div className="route-frame">{children}</div></ViewTransition>
  </HistoryMotionContext.Provider>
}

function ContentMotion({ target }: { target: RefObject<HTMLElement | null> }) {
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const query = searchParams.toString()
  const historyPath = useContext(HistoryMotionContext)
  useContentMotion(pathname + (query ? `?${query}` : ""), { native: true, historyPath, target })
  return null
}

export function PageTransition({ children, className }: { children: ReactNode; className: string }) {
  const ref = useRef<HTMLElement>(null)
  return <ViewTransition name="page-content" default="route-content">
    <main ref={ref} className={`route-content ${className}`}>
      <Suspense fallback={null}><ContentMotion target={ref} /></Suspense>
      {children}
    </main>
  </ViewTransition>
}
