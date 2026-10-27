# 后台经营分析与站点外观实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现用户选择的第三套后台设计；横幅和配色后台统一配置，前台只保留语言和明暗选择。

**Architecture:** 在现有 Next.js、Spring Boot 项目内修改；沿用后台鉴权、配置表、上传和统计接口。新建 `/admin/appearance`。根布局读取服务端配色，忽略旧浏览器配色偏好；保留语言与明暗偏好。

**Tech Stack:** React 19、Next.js 16、TypeScript、Recharts、Lucide、Spring Boot/JPA；不引入依赖。

**Spec:** 第三张后台设计 `C:/Users/F7733/.codex/generated_images/01a17bcc-d7d7-7353-aea2-f14540182af2/exec-570b8817-bc8f-409e-862f-16aa6668e052.png`。

## Global Constraints
- 保留此前 USDT/积分删除和前台重构，不覆盖真实商品或配置。
- 使用真实成交和访问数据；成交口径为已付款/已发货，零数据有清晰状态。
- 成交额与订单数分图、分单位；支持日期范围和 7/30 天，补零但不虚构成交。
- 横幅与配色入库，向新访问及刷新后的客户端下发。
- 外观 URL 只允许站内绝对路径或 HTTP/HTTPS，拒绝脚本协议；配色枚举验证。
- 使用现有字体、图片和图标；320/390/768/1440px 与亮暗模式适配。

## Review Focus
- 非管理员不能改配置，非法 URL/配色不能保存。
- 浏览器旧配色不覆盖站点设置，初次访问获得已保存配置。
- 横幅开关/手机图回退生效；图表日期、单位和数据一致。
- 补货/订单查看定位真实对象，移动端表单和抽屉不溢出。

## Tasks
- [x] 配置边界测试先失败，再实现并通过；图表日期范围通过浏览器检查。
- [x] 外观配置持久化、公开下发、首屏配色与刷新机制。
- [x] 后台侧栏、顶栏、手机抽屉与统一样式。
- [x] 分层数据、独立趋势图、库存分组、订单查看。
- [x] 外观表单：上传、配色、横幅预览、保存。
- [x] Maven package、类型检查、前端构建、浏览器和配置读回验证。
- [x] 图片对比验收并更新 design-qa.md。

## Decisions
- 在现有工作区实现，不创建新原型；已有必要未提交改动。
- 原图示例订单/数值不进入数据库，数据少时仍保持视觉结构。
- 新 URL 校验仅限定外观配置，不扩展其他历史设置。
