import { getCatalogProducts, getCategories, getSiteConfig } from "@/services/api-server"
import { HomeContent } from "./home-content"
import type { Metadata } from "next"

export async function generateMetadata(): Promise<Metadata> {
  try {
    const config = await getSiteConfig()
    return {
      title: config.site_name || "Orion Key",
      description: config.site_description || config.site_slogan || "",
      alternates: { canonical: "/" },
      openGraph: {
        title: config.site_name || "Orion Key",
        description: config.site_description || config.site_slogan || "",
        url: "/",
        ...(config.logo_url ? { images: [{ url: config.logo_url }] } : {}),
      },
    }
  } catch {
    return { title: "Orion Key" }
  }
}

export default async function HomePage() {
  const [products, categories, config] = await Promise.all([
    getCatalogProducts().catch(() => []),
    getCategories().catch(() => []),
    getSiteConfig().catch(() => null),
  ])

  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "WebSite",
    name: config?.site_name || "Orion Key",
    description: config?.site_description || config?.site_slogan || "",
  }

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
      <HomeContent
        products={products}
        categories={categories}
        siteSlogan={config?.site_slogan || ""}
        siteDescription={config?.site_description || ""}
      />
    </>
  )
}
