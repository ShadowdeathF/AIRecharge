# 石墨经营工作台实施计划

> **For agentic workers:** Use superpowers:subagent-driven-development for the backend task and a scoped review; integrate and verify the frontend in the same session. This file is the progress ledger.

**Goal:** 落地最新第一张设计图，支持任意付款日期范围的汇总、趋势、当天订单追查与对账导出。

**Spec:** `exec-9c234fb0-ca31-43e8-8418-6d331e9da334.png`，2026-10-28 本轮生成图片中第一个展示结果。深灰中性底、扁平指标、主趋势/明细、右侧当天订单。

**Architecture:** 保留既有业务与旧接口，新增只读 `/admin/workbench` API。所有范围以 Asia/Shanghai 付款时间为准，状态仅 PAID/DELIVERED，半开区间包含结束日。前端使用现有字体、Lucide、Recharts、鉴权和上传体系，不加依赖。

## Constraints and rulings

- 当前分支 `feat/admin-graphite-workbench`；沿用本机已有运行环境，在当前目录实施，避免复制凭据和数据库到另一个工作目录。
- 选图里的“批量标记已发货”不直接改变履约状态；采用“导出所选”并跳转现有订单管理处理。
- 没有渠道账单数据，不把订单实收叫到账/利润；明确“渠道到账及手续费尚未核对”。第一套没有导入流程，本轮不虚构账单匹配。
- 不写入示例订单。浏览器可用隔离响应验证多页/空数据，真实页面使用实际数据。
- 图中无待制作的位图插画；沿用现有品牌图标与字体，不新增装饰资产。
- 保留前台、USDT/积分移除、统一后台配色和语言/明暗设置。
- 勾选导出限100笔并明确提示，避免 GET 查询 ID 列表超过常见请求头限制；整个筛选范围的导出仍支持10000笔。

## API contract (snake_case)

`GET /admin/workbench/overview?start_date&end_date&payment_method`:

```ts
type Summary = { sales_amount:number; order_count:number; average_order:number; undelivered_count:number }
type Overview = {
 start_date:string; end_date:string; summary:Summary;
 comparison:{start_date:string;end_date:string;summary:Summary};
 trend:{date:string;sales_amount:number;order_count:number;previous_sales_amount:number;previous_order_count:number}[];
 low_stock_products:{product_id:string;title:string;available_stock:number;threshold:number}[];
 reconciliation_status:"NOT_IMPORTED"
}
```

`GET /admin/workbench/orders?start_date&end_date&payment_method&status&keyword&page&page_size`:

```ts
type WorkbenchOrder = {id:string;payment_reference:string|null;paid_at:string;created_at:string;actual_amount:number;status:"PAID"|"DELIVERED";payment_method:string|null;product_title:string;item_count:number}
type Ledger = {list:WorkbenchOrder[];pagination:{page:number;page_size:number;total:number};totals:{sales_amount:number;order_count:number}}
```

`GET /admin/workbench/export` takes the same filters plus optional comma-separated `ids`. CSV with UTF-8 BOM, proper escaping/formula protection, maximum 10000 rows with explicit error instead of truncation. Returned IDs must also match filters. Existing `/admin/orders/{id}` supplies full order details.

Validation: ISO date required, start<=end, end<=Shanghai today, at most 366 inclusive days, page>=1, page_size 1..100, keyword max100; only PAID/DELIVERED status. Channel optional. Equal-length previous range ends one day before current start; trend zero-filled. Use database filtering/aggregation, deterministic paid_at/id sort, no unbounded `findAll()`.

## Tasks

- [x] Backend: add controller/service/query components and integration tests. First exercise missing routes/required behavior, then implement. Tests cover half-open dates, status/channel/keyword filters, previous period, zero days, summary/totals equality, pagination, CSV selected IDs/formula protection/export bound and validation/admin auth. Run Maven tests with local JDK22; do not repackage the running API jar.
- [x] Frontend: add types/API client; replace dashboard with range controls, 4 interactive metrics, amount/orders chart toggle and comparison, clickable/keyboard day selection, day inspector, pageable searched/status-filtered ledger, selection sums and CSV downloads. Make order detail/search/operational links work. Preserve neutral light mode and theme tokens, improve dark surfaces globally.
- [x] Integrate: stop managed processes only after PID/start time/path identity checks; Maven package (tests included), build web, TypeScript check, restart previews. Real API smoke and browser checks for live filters, day drilldown, empty state, paging, exports, range errors, auth and 320/390/768/1440px + light/dark.
- [x] Review: backend scoped and final whole change review; resolve material findings. Compare reference and actual screenshot in one image, update `design-qa.md` to passed, preserve real-data differences.
- [x] Git: commit/publish verified snapshot through the authorized GitHub connection, preserve upstream and existing main history, verify matching trees and CI. Exclude `.local`, credentials, database and runtime artifacts.

## Verification commands

```powershell
# Maven test (JAVA_HOME points to .local/tools/jdk-22.0.2+9)
mvn -B -ntp -f apps/api/pom.xml -Dspring.mail.host=127.0.0.1 -Dspring.mail.username=ci@example.invalid -Dmail.enabled=false test
# after stopping managed API: .local/build-api.ps1
# after stopping managed web: .local/web-build.ps1
node apps/web/node_modules/typescript/bin/tsc --noEmit -p apps/web/tsconfig.json
git diff --check
```
