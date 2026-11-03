// Identity belongs to a page session; appearance and language remain browser preferences.
const keys = { auth: "auth_token", profile: "userProfile", guest: "session_token" } as const
let initialized = false
let version = 0

function storage(): Storage | null {
  if (typeof window === "undefined") return null
  if (!initialized) {
    // A shared legacy token cannot identify which account an existing tab intended to use.
    // Require one fresh login instead of copying that possibly incorrect identity to every tab.
    for (const key of Object.values(keys)) window.localStorage.removeItem(key)
    initialized = true
  }
  return window.sessionStorage
}

export function getAuthToken(): string | null { return storage()?.getItem(keys.auth) ?? null }
export function getSessionVersion(): number { return version }
export function setAuthToken(token: string) {
  const current = storage(), previous = current?.getItem(keys.auth)
  if (previous && previous !== token) version++
  current?.setItem(keys.auth, token)
}
export function clearAuthToken() { version++; const current = storage(); current?.removeItem(keys.auth); current?.removeItem(keys.profile) }
export function getGuestToken(): string | null { return storage()?.getItem(keys.guest) ?? null }
export function setGuestToken(token: string) { storage()?.setItem(keys.guest, token) }
export function clearGuestToken() { version++; storage()?.removeItem(keys.guest) }
export function getSessionOwner(): string { return `${getSessionVersion()}:${getAuthToken() || "guest"}:${getGuestToken() || ""}` }

export function getStoredUser<T>(): T | null {
  const current = storage()
  if (!getAuthToken()) { current?.removeItem(keys.profile); return null }
  try { return JSON.parse(current?.getItem(keys.profile) || "null") as T | null }
  catch { current?.removeItem(keys.profile); return null }
}
export function setStoredUser(user: unknown | null) {
  const current = storage()
  if (user) current?.setItem(keys.profile, JSON.stringify(user))
  else current?.removeItem(keys.profile)
}
