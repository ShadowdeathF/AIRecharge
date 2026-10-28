# 浅薄荷绿横幅与商品图取景修正

- 使用内置Image Gen编辑上一版横幅，保留右侧翡翠玻璃主体，改为明亮薄荷绿背景与柔和日光。未调用CLI/API回退。
- 实际生成尺寸1998×787；只转换为WebP减小资源体积，未改变原图构图。发布资源：`apps/web/public/images/store/hero-emerald-v3.webp`。
- 本地原图：`.local/design/desktop-banner-emerald-bright-original.png`。后台电脑横幅地址：`/images/store/hero-emerald-v3.webp`。
- 前台和后台预览改为浅色文字保护层及深绿色文字，不再叠加黑色遮罩。仅左侧文字区域需要保护，右侧展示图像本色。亮暗模式都保留明亮横幅。
- 普通商品改为横向短缩略图，消除原竖长框带来的上下背景带。对四角为浅中性色、内部为明确深色矩形卡面的图片，客户端低分辨率分析后仅改变展示取景，不改文件或URL。照片、透明图、靠边内容和无法读取的跨域图保持原有展示方式。

## 最终编辑提示词

```text
Edit this existing website banner background. Preserve the elegant emerald glass flowing loop sculpture and its right-side placement, but completely relight the scene so it is clearly BRIGHT, airy, refined and inviting. Replace the near-black background with a luminous pearl / very pale mint studio backdrop (#e9f5ef), including the entire left 55 percent which must be clean bright negative space for dark HTML heading text. The right-side sculpture is glossy emerald and clear green glass with strong silver-white highlights, visible translucent edges, and a soft realistic studio shadow on the light mint stage. Bright professional daylight softbox lighting, visibly higher exposure, superb clarity and material detail. No black backdrop, no dark vignette, no black gradient overlay, no muddy or underexposed areas. Keep a delicate premium editorial technology feel; avoid neon or cartoon effects. Output one panoramic desktop banner background, target 2600 x 600 pixels, very wide; preserve the sculpture's recognizability within a shallow website hero crop. Main subject at about 80 percent horizontal position. Left side almost uniform light mint with very faint texture and plenty of usable quiet space. NO text, NO logos, NO words, NO numbers, NO website UI, NO buttons, NO watermarks. This is an actual asset, not a screenshot. Opaque background.
```
