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
