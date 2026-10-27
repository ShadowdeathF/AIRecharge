# 清爽蓝白商城重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Orion Key 商城中实现用户选定的第三套布局，并支持电脑、手机和主题切换。

**Architecture:** 直接修改现有 Next.js 商城页面与共享组件，保留真实 API、商品、库存、规格、价格及购物流程。已有后台业务与此前 USDT、积分删除改动保持不变。沿用当前图标库和主题上下文。

**Tech Stack:** Next.js 16、React 19、TypeScript、Tailwind CSS、Lucide。

**Spec:** 选定图 `C:/Users/F7733/.codex/generated_images/01a17bcc-d7d7-7353-aea2-f14540182af2/exec-56e17c99-8d40-490b-a541-9a133ddc2b8e.png`。白色页面、蓝色操作、紧凑横幅、桌面主推商品和列表分栏、手机竖排与底部导航。用户已授权本机 Chrome 验证。

## Global Constraints
- 保留真实商品内容与后台配置；不把设计图的示例商品和价格写入数据库。
- 不新增后端功能、支付渠道或兑换流程。
- 默认蓝色，提供亮色/暗色/跟随系统及已有配色；持久化选择并避免首屏闪烁。
- 320–1440px 可操作，无水平溢出，固定导航不能遮住购买或表单控件。
- 使用生成图片作为横幅和缺省封面，已有商品封面优先；不画假图或虚构授权。

## Review Focus
- 无库存、单商品、筛选为空、长中文标题不破坏布局。
- 搜索、分类、排序和价格筛选保留可用。
- 主题与配色刷新后保留，系统模式响应系统变化。
- 未登录/已登录导航正确，移动端不遮挡按钮。
- 商品详情、购物车、订单查询和登录继续可访问。

## Tasks
- [x] 1. 保存基线并建立浏览器检查：布局、主题、空搜索、导航、横向溢出。
- [x] 2. 生成并保存横幅、AI 商品图和通用数字商品图。
- [x] 3. 重构商城导航、主题菜单、底部导航与全局商城样式。
- [x] 4. 重构首页和商品卡片，保留搜索筛选；统一商品详情图及购买区视觉。
- [x] 5. 生产构建、类型检查、重启、电脑/手机浏览器交互与截图验证。
- [x] 6. 将选定图与实测截图放到同一输入比较，修复问题并记录 design-qa.md。

## Execution notes
- Ruling: 在现有工作区直接修改 — 用户要求二开当前运行项目，且工作区包含上一任务的必要未提交改动，不创建缺少这些改动的新原型。
- Ruling: 现有字体 Inter 配合 Windows 系统中文字体、图标 Lucide — 项目已具备且与选定图匹配，不增加依赖或远程字体请求。
- Ruling: 设计图为示例数据，验收布局与交互不以示例商品名和价格强行覆盖真实数据。
- Ruling: 保留 Inter 字体但改为本地 WOFF2 — 最后一次构建出现 Google Fonts 下载失败；字体来自 Inter 官方仓库，许可同目录保存。
- Final verification: production build、TypeScript、diff check 通过；两组浏览器验证共 19 项通过，0 页面错误；design-qa.md passed。
