export function paginateCatalog<T>(items: T[], requestedPage: number, pageSize: number) {
  const totalPages = Math.max(1, Math.ceil(items.length / pageSize))
  const page = Math.min(totalPages, Math.max(1, Math.floor(requestedPage) || 1))
  const start = (page - 1) * pageSize
  return { page, totalPages, items: items.slice(start, start + pageSize) }
}

export function catalogPageNumbers(page: number, total: number, mobile = false): (number | null)[] {
  if (mobile) {
    const start = Math.max(1, Math.min(page - 1, total - 2))
    return Array.from({ length: Math.min(3, total) }, (_, i) => start + i)
  }
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  if (page <= 4) return [1, 2, 3, 4, 5, null, total]
  if (page >= total - 3) return [1, null, total - 4, total - 3, total - 2, total - 1, total]
  return [1, null, page - 1, page, page + 1, null, total]
}

interface CatalogResponse<T> { list: T[]; pagination: { page_size: number; total: number } }

/** Existing filters work over the full catalog, including products after the API's first page. */
export async function loadCatalogProducts<T extends { id: string }>(fetchPage: (page: number, size: number) => Promise<CatalogResponse<T>>): Promise<T[]> {
  const first = await fetchPage(1, 100)
  const size = Math.max(1, first.pagination.page_size)
  const lastPage = Math.ceil(first.pagination.total / size)
  const products = [...first.list]
  for (let start = 2; start <= lastPage; start += 4) {
    const pages = await Promise.all(Array.from({ length: Math.min(4, lastPage - start + 1) }, (_, offset) => fetchPage(start + offset, size)))
    pages.forEach(page => products.push(...page.list))
  }
  return [...new Map(products.map(product => [product.id, product])).values()]
}
