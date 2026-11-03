# Procurement, company pricing and profit accounting implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver enterprise online/offline pricing, purchase-backed inventory costs, a reconciled trade ledger, profit settlements and quarterly dividends.

**Architecture:** Reuse Spring Boot/JPA and Next.js. Introduce a bounded `com.orionkey.finance` module for procurement, companies and financial records; integrate pricing at existing product/cart/order boundaries and cost attribution at delivery. Immutable settlement JSON documents preserve calculation inputs; typed JPA records store stock allocations, financial postings, cash movements and audit history.

**Tech Stack:** Java 22, Spring Boot 3.4.1, JPA, PostgreSQL; existing Next.js/React/TypeScript UI and H2 integration tests. No additional dependencies.

**Spec:** `docs/superpowers/specs/2026-11-02-company-pricing-profit-accounting-design.md`, as amended by the user's 2026-11-03 instructions below.

## Global constraints and approved amendments

- User approved implementation, cumulative original-settlement referral corrections and personal negative balances with offset/recovery registration.
- Procurement supplies the cost: monthly bulk purchases and supplemental purchases use one workflow; quantity one is a valid supplemental purchase.
- Record actual receipt lots and issue quantities; new transactions do not treat product default costs as actual procurement cost. Missing legacy procurement links remain unknown.
- No referrer means the referral 13.5% of total profit is credited to a separate company dividend reserve, not an unassigned personal reward.
- Quarterly dividends use only that reserve, not the separate 10% operating reserve. Default recipients are the three existing role holders with equal weights; weights/persons may be changed for future settlements.
- All customer prices and costs are snapshots; preserve anonymous retail, quotes per SKU, online/offline de-duplication, precise money, immutable closed documents and administrator-only financial access.
- No real payments, supplier purchases or automated transfers are issued by these administrative records.

## Review focus

1. Concurrent stock allocations, including two identical SKUs within one order, must not overspend a receipt lot or double-book its cost.
2. Company login/logout, account reassignment and stale displayed quotes must not leak prices or silently create a different-priced order.
3. One-cent amounts, negative profits, a later referral assignment and a refund after quarterly payout must conserve money and retain original people/weights.
4. Unlinked legacy card batches, partial receipts and partial customer payments must remain visibly incomplete rather than appearing profitable.
5. Retry, CSV duplicate, simultaneous settlement and cash-recovery actions must remain idempotent and auditable.

## Task 1: Procurement and financial calculation core

**Files:** Create `apps/api/src/main/java/com/orionkey/finance/ProfitMath.java`, `ProcurementMath.java`; tests `finance/ProfitMathTests.java`, `ProcurementMathTests.java`.

**Interfaces:** `ProfitMath.calculate(List<ProfitLine>, Map<String,UUID>) -> Distribution` returns raw and rounded person shares, operating base/tail and no-referrer dividend reserve. `ProfitMath.weighted(BigDecimal, List<Weight>) -> Map<UUID,BigDecimal>` allocates exact cents. `ProcurementMath.issueCost(totalCost,totalQuantity,previouslyIssued,quantity) -> BigDecimal` uses cumulative rounding so allocated costs sum to the receipt total.

- [ ] Write failing tests for the calculator's ordinary/loss/referral examples, no-referrer reserve, equal/custom quarterly weights, cumulative corrections and a 0.01-cost receipt split three ways.
- [ ] Run the focused Maven tests and inspect expected missing-feature failures.
- [ ] Implement exact BigDecimal calculation and cumulative stock-cost allocation; reject invalid quantities, nonfinite/overscale money and duplicate identities.
- [ ] Run the focused tests; commit the independently tested calculation core.

## Task 2: Procurement, receipts and traceable stock issues

**Files:** Create finance entities/repositories/services for purchases, planned lines, receipt lots and allocations; `AdminProcurementController`; migration SQL. Modify `CardImportBatch`, card import and delivery integration; tests `ProcurementIntegrationTests`.

**Interfaces:** `ProcurementService.createPurchase(request,actor)`, `receive(purchaseId,request,actor)`, `allocate(tradeId,lineKey,productId,specId,quantity,lotIds,actor)`. Receipt lots store quantity, total landed cost and consumed quantity/cost. Existing card import batches optionally link one receipt lot.

- [ ] Test quantity-one supplement and monthly multi-SKU purchase, partial receiving, exact issue costs, mixed-lot issues, duplicate receiving, wrong-SKU/batch association and stock exhaustion/concurrency.
- [ ] Implement typed persistent purchases/lines/lots/allocations with locks, versions, source references and idempotency keys; receipts can attach new/existing card batches without duplicating inventory.
- [ ] Bind online delivered keys to actual purchase lots; legacy unknown batches stay pending. Allocate offline inventory only through an explicit issue/association action.
- [ ] Run focused tests and existing delivery/order regressions; commit.

## Task 3: Company membership and authoritative agreement pricing

**Files:** Company/CompanyQuote entities and repositories, `CompanyPricingService`, `AdminCompanyController`; existing user/order/order-item entities, product/cart/order services, types and client pricing hook; tests `CompanyPricingIntegrationTests`.

**Interfaces:** `CompanyPricingService.resolve(userId,product,specId,quantity) -> PriceQuote` supplies unit price, source, company snapshot and quote version. Current authenticated company price data is privately cached only in that account's client context.

- [ ] Test two companies, exact SKU matching, retail fallback, quote version changes, cart/direct agreement pricing, expected-total mismatch and cross-company access denial.
- [ ] Implement admin company/account/quote management, shared server pricing, order snapshots and stale-price rejection. Reuse pricing in cart and product display; clear account-specific data at logout/switch.
- [ ] Verify old orders/re-pay remain frozen and existing retail tests pass; commit.

## Task 4: Unified ledger, receipts and procurement-backed costs

**Files:** FinanceTrade and snapshot lines, cash/audit records, `FinanceTradeService`, reconciliation controller and CSV preview/commit, tests `FinanceTradeIntegrationTests`.

**Interfaces:** `syncPaidOrders()`, `createOffline(request,actor)`, `recordReceipt(...)`, `confirm(...)`, `allocateCosts(...)` produce one finance transaction per source order or company/external reference. Income and cost completeness are separate from payment/delivery status.

- [ ] Test online/offline visibility, mixed SKUs, partial/final receipts, cost unknown vs zero, explicit stock issue costs, repeat sync and import de-duplication.
- [ ] Implement ledger queries, drilldowns, edits with version validation, confirmation invalidation, CSV preview/commit/export and explicit inventory cost allocation.
- [ ] Verify no financial edit overwrites payment facts and incomplete trades cannot settle; commit.

## Task 5: Profit settlements, reserves, quarterly dividends and corrections

**Files:** FinancePerson, FinanceSettlement, FinancePosting, FinanceAdjustment and cash movements; `FinanceSettlementService`, controller; tests `FinanceSettlementIntegrationTests`.

**Interfaces:** profit preview/confirm takes confirmed transaction IDs; quarter preview/confirm takes a quarter and configured recipient weights. Postings distinguish `PERSONAL_PAYABLE`, `OPERATING_RESERVE` and `DIVIDEND_RESERVE`. Corrections recompute cumulative targets of their original frozen group and append only differences.

- [ ] Test known-person reward, no-referrer reserve, operating expenses, equal/custom quarter policies, period eligibility, duplicate/concurrent confirmation and original-person refunds/recoveries.
- [ ] Implement immutable profit snapshots and postings, role/person management, expenses and manual cash records; one confirm per selection or quarter using locks and unique references.
- [ ] Implement original-group cumulative referral/financial corrections. If the group already funded a quarterly dividend, append cumulative corrections to that original quarter's people/weights; never silently move old losses to a new policy.
- [ ] Run calculation + settlement suites and verify money conservation for tiny and negative values; commit.

## Task 6: Integrated responsive administration and storefront pricing

**Files:** `apps/web/types/finance.ts`, finance API methods; admin `/procurement`, `/reconciliation`, `/profit-sharing` pages and focused components; sidebar; account pricing consumers and cart/checkout.

- [ ] Add frontend behavior tests for CSV parsing, stale responses, no-referrer display, stock-cost status, quarter policy normalization and pennies.
- [ ] Build procurement purchase/receipt/stock views, a real reconciliation route with company quotes/accounts and offline entry/import/detail, and profit/reserve/quarter/people/cash views. Reuse existing theme tokens and accessible dialogs/tables.
- [ ] Connect enterprise pricing on home/product/cart/order paths, keep desktop/mobile catalog dimensions and existing appearance controls.
- [ ] Run TypeScript, production build and focused browser journeys (390/1440, light/dark, company switch, purchase receive/issue, offline reconciliation, settlement/quarter previews).
- [ ] Commit the connected frontend after its backend and behavior checks pass.

## Task 7: Migration, integration, review and delivery

- [ ] Run full Maven `package` and existing/new frontend tests; verify explicit PostgreSQL migration in an isolated local database and preserve existing demo data.
- [ ] Have a fresh reviewer inspect the complete branch for spec coverage, access control, stock/money conservation and user-visible failures; fix material findings with regression tests.
- [ ] Integrate the verified branch into the user's checkout, update local runtime and record deployment boundaries. Do not publish private dumps/configuration or claim real payment/supplier fulfillment.

## Execution rulings

- 2026-11-03: Existing user authorization covers implementing the approved design and the newly specified procurement/dividend rules; continue in-session without another permission loop. An isolated Git worktree protects the current checkout and local runtime. This plan uses native execution with a fresh final review.
- Cost allocation preserves actual card receipt attribution online; selecting arbitrary newer/cheaper stock to cost already delivered cards is disallowed.
- Quarterly configuration is versioned at confirmation. Equal weights are stored as `1:1:1`, avoiding an invented `33.33%` total of 99.99%; indivisible cents use deterministic largest-remainder allocation shown in the preview.
