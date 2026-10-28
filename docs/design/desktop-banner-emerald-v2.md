# 翡翠玻璃电脑端横幅

- 使用内置 image_gen 生成新的位图背景；未调用 CLI/API 回退。
- 发布资源：`apps/web/public/images/store/hero-emerald-v2.webp`，2120×742，约75KB。
- 生成原图保存在本地 `.local/design/desktop-banner-emerald-original.png`，未修改构图。发布版本仅转换为 WebP 以减小传输体积。
- 后台“站点外观 → 电脑端横幅图”填 `/images/store/hero-emerald-v2.webp`。标题与说明继续使用后台可编辑文字；手机独立图片配置不变，留空时按现有逻辑回退电脑图。
- 横幅以 cover 等比铺满，按容器比例裁切；左侧遮罩和浅色文字保证可读性，亮暗模式都使用完整颜色。原有商品图、手机图和站点配色无需修改。

## 最终生成提示词

```text
Use case: ads-marketing / stylized-concept.
Asset type: a single finished desktop website hero BACKGROUND IMAGE for the AIRecharge digital AI subscription storefront. This is the actual image asset, not a website screenshot, not a mockup, no UI chrome.
Primary request: a beautiful restrained luxury technology banner with sculptural emerald glass and dark brushed metal. Ultra-wide horizontal canvas, target 3200 x 560 pixels, approximately 5.7:1 ratio. Design must remain elegant when displayed at about 1352 x 196 pixels.
Composition: edge-to-edge seamless cinematic composition. Keep the left 58 percent as calm almost-black graphite negative space, subtly textured, so real HTML heading text can be placed there later. Concentrate one refined three-dimensional continuous folded loop / flowing ribbon of translucent deep emerald glass in the right third, around 80 percent across and centered vertically. A few precise silver metal edges and a tiny amount of diffused mint light give depth, like an exceptional industrial design product photograph. The sculptural object must be visually understandable at small banner height. Keep its recognizable core inside the middle 60 percent of image height to survive responsive cropping. Shape may extend beyond the right edge naturally; no separate panel or dividing line.
Palette: graphite #101416, very deep forest green #073b32, emerald #10b981, desaturated soft mint highlights, tiny cool silver accents. No blue-violet gradients. Crisp material reflections, velvety dark background, soft controlled studio light, tasteful subtle dimensional shadow, premium realistic CGI, confident quiet editorial art direction, exquisite detail with substantial negative space. Background and sculpture should merge naturally; no glowing neon cyberpunk excess, no starfield, no excessive particles.
Text: NO TEXT, no words, no numbers, no logos, no badges, no watermarks. Do not create fake UI, buttons, cards, diagrams, cryptocurrency, payment symbols, padlocks, literal keys, or floating app windows. Exactly one panoramic image, not variants or a contact sheet. Opaque background, not transparent.
```

提示词中的3200×560为构图目标；模型实际返回2120×742。网页在固定高度的横幅容器内等比裁切，不拉伸原图。
