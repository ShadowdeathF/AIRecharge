import React from "react"
import type { Metadata } from "next"
import localFont from "next/font/local"
import { Toaster } from "sonner"
import { AppProviders } from "@/lib/context"
import { getSiteConfig } from "@/services/api-server"
import { getSiteColor } from "@/lib/appearance"
import { ThemeScript } from "./theme-script"
import { DynamicFavicon } from "@/components/dynamic-favicon"
import "./globals.css"

const inter = localFont({ src: "../public/fonts/InterVariable.woff2", variable: "--font-inter", weight: "100 900", display: "swap" })

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_BASE_URL || "http://localhost:3000"),
  title: {
    template: "%s | Orion Key",
    default: "Orion Key - Auto Card Delivery",
  },
  description: "Automated virtual goods card delivery platform",
  robots: { index: true, follow: true },
  openGraph: {
    type: "website",
    locale: "zh_CN",
    siteName: "Orion Key",
  },
  twitter: {
    card: "summary_large_image",
  },
}

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  const config = await getSiteConfig().catch(() => null)
  const colorScheme = getSiteColor(config?.store_theme_color)
  return (
    <html data-color={colorScheme} lang="zh" suppressHydrationWarning>
      <head>
        <ThemeScript colorScheme={colorScheme} />
      </head>
      <body className={`${inter.variable} font-sans antialiased`} suppressHydrationWarning>
        <AppProviders initialConfig={config}>
          <DynamicFavicon />
          {children}
          <Toaster position="top-center" richColors closeButton />
        </AppProviders>
      </body>
    </html>
  )
}
