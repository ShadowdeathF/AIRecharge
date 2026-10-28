export interface ArtworkBounds { x: number; y: number; width: number; height: number }

/** Recognize a dark rectangular card surrounded by a broad, light neutral backdrop.
 * Photos, full-bleed art and ambiguous images retain their original framing. */
export function detectArtworkBounds(data: Uint8ClampedArray, width: number, height: number): ArtworkBounds | null {
  if (width < 8 || height < 8 || data.length < width * height * 4) return null
  const corners = [0, (width - 1) * 4, (height - 1) * width * 4, (width * height - 1) * 4]
  if (corners.some(i => data[i + 3] < 250 || Math.min(data[i], data[i + 1], data[i + 2]) < 140 || Math.max(data[i], data[i + 1], data[i + 2]) - Math.min(data[i], data[i + 1], data[i + 2]) > 60)) return null
  let left = width, top = height, right = -1, bottom = -1, dark = 0
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    const i = (y * width + x) * 4
    if (data[i + 3] >= 250 && Math.max(data[i], data[i + 1], data[i + 2]) < 125) {
      left = Math.min(left, x); right = Math.max(right, x)
      top = Math.min(top, y); bottom = Math.max(bottom, y); dark++
    }
  }
  const w = right - left + 1, h = bottom - top + 1
  if (w < width * .3 || h < height * .2 || w > width * .86 || h > height * .8 || left < width * .07 || top < height * .07 || right > width * .93 || bottom > height * .93 || dark / (w * h) < .6) return null
  const pad = Math.max(1, Math.round(Math.max(width, height) * .015))
  return { x: (left - pad) / width, y: (top - pad) / height, width: (w + pad * 2) / width, height: (h + pad * 2) / height }
}
