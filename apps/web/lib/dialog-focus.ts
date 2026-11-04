let openDialogs = 0
let originalOverflow = ""

export function lockPageScroll() {
  if (openDialogs === 0) originalOverflow = document.body.style.overflow
  openDialogs++
  document.body.style.overflow = "hidden"
  let released = false
  return () => {
    if (released) return
    released = true
    openDialogs--
    if (openDialogs === 0) document.body.style.overflow = originalOverflow
  }
}

export function containDialogFocus(element: HTMLElement) {
  const previousFocus = document.activeElement
  element.dataset.modalFocus = "true"
  const controls = () => Array.from(element.querySelectorAll<HTMLElement>(
    'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
  )).filter(control => control.getClientRects().length > 0 && !control.closest("[inert]"))
  const first = () => controls()[0] || element
  const frame = requestAnimationFrame(() => first().focus({ preventScroll: true }))
  const handleTab = (event: KeyboardEvent) => {
    const scopes = document.querySelectorAll('[data-modal-focus="true"]')
    if (event.key !== "Tab" || scopes[scopes.length - 1] !== element) return
    if (document.activeElement?.closest("dialog[open]")) return
    const targets = controls()
    const edge = event.shiftKey ? targets[0] : targets[targets.length - 1]
    if (document.activeElement === edge || !element.contains(document.activeElement) || targets.length === 0) {
      event.preventDefault()
      const focusTarget = (event.shiftKey ? targets[targets.length - 1] : targets[0]) || element
      focusTarget.focus({ preventScroll: true })
    }
  }
  document.addEventListener("keydown", handleTab)
  return () => {
    delete element.dataset.modalFocus
    cancelAnimationFrame(frame)
    document.removeEventListener("keydown", handleTab)
    if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus({ preventScroll: true })
  }
}
