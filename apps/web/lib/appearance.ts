export type ColorScheme = "orange" | "coral" | "emerald" | "blue" | "pink" | "purple"
export const COLOR_SCHEMES: { key: ColorScheme; label: string; color: string }[] = [
  { key: "blue", label: "经典蓝", color: "#2563eb" },
  { key: "emerald", label: "翡翠绿", color: "#159a73" },
  { key: "orange", label: "琥珀橙", color: "#e57b14" },
  { key: "coral", label: "珊瑚红", color: "#ee5938" },
  { key: "purple", label: "鸢尾紫", color: "#9050d6" },
  { key: "pink", label: "玫瑰粉", color: "#e13e76" },
]
export function getSiteColor(value?: string): ColorScheme {
  return COLOR_SCHEMES.some(s => s.key === value) ? value as ColorScheme : "blue"
}
export function isSafeSiteUrl(value: string) {
  if (!value) return true
  if (value !== value.trim() || /[\u0000-\u0020\u007f\\]/.test(value)) return false
  if (value.startsWith("/") && !value.startsWith("//")) return true
  try { const url = new URL(value); return ["http:", "https:"].includes(url.protocol) && !url.username && !url.password } catch { return false }
}
