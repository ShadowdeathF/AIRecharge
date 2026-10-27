/** Only used when a merchant has not supplied a product cover. */
export function getProductArtwork(product: { cover_url?: string; title: string }) {
  if (product.cover_url) return product.cover_url
  return /chatgpt|openai/i.test(product.title) ? "/images/store/product-ai.webp" : "/images/store/product-digital.webp"
}
