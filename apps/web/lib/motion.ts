export const fluidSpring = { type: "spring", visualDuration: .32, bounce: .1 } as const

export function getReducedMotion() {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches
}

export function subscribeReducedMotion(onChange: () => void) {
  const preference = window.matchMedia("(prefers-reduced-motion: reduce)")
  preference.addEventListener("change", onChange)
  return () => preference.removeEventListener("change", onChange)
}

let themeTransition: ViewTransition | undefined
let themeVersion = 0

export function transitionTheme(update: () => void) {
  const version = ++themeVersion
  themeTransition?.skipTransition()
  if (getReducedMotion() || !document.startViewTransition) {
    update()
    return
  }
  const transition = document.startViewTransition(() => {
    if (version === themeVersion) update()
  })
  themeTransition = transition
  const unsubscribe = subscribeReducedMotion(() => {
    if (getReducedMotion()) transition.skipTransition()
  })
  void transition.ready.catch(() => {})
  void transition.finished.catch(() => {}).finally(() => {
    unsubscribe()
    if (themeTransition === transition) themeTransition = undefined
  })
}

export function animateContent(element: HTMLElement | null, reducedMotion: boolean): Animation | undefined {
  if (reducedMotion || !element?.animate) return

  return element.animate(
    [
      { opacity: 0, transform: "translateY(8px)" },
      { opacity: 1, transform: "translateY(0)" },
    ],
    { duration: 240, easing: "cubic-bezier(0.22, 1, 0.36, 1)", fill: "none" },
  )
}
