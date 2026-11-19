# Frontend guide: admin dashboard split into summary + charts

**Status:** breaking change — the old single-endpoint response shape is gone, not deprecated.

This guide is for the frontend team. It covers what changed, every field in both new payloads, how
each field is computed, and how to use it. The per-endpoint reference is
[`get-dashboard.md`](get-dashboard.md) and [`get-dashboard-charts.md`](get-dashboard-charts.md);
this file is the "what do I have to change" view.

---

## 1. What changed, in one minute

One endpoint became two:

| Before | After |
|---|---|
| `GET /api/admin/dashboard?range=week` | `GET /api/admin/dashboard` — **no** time filter |
| | `GET /api/admin/dashboard/charts?range=week` — the only time-based endpoint |

The old response mixed two kinds of number in one payload: lifetime totals (`users.total`) and
per-range counters (`users.newInRange`). You had to read the field name to know which was which.
Now the split is structural — the summary has no time dimension at all, and everything that moves
over time lives on the charts endpoint.

**Three consequences for your code:**

1. Remove the `range` query parameter from the summary call. It is ignored now.
2. Any field ending in `InRange` / `new*` must move to the charts endpoint.
3. The `alerts`, `breakdowns`, and `tables` blocks no longer exist. See §2 for what replaces each.

---

## 2. Removed fields → what to use instead

| Removed | Replace with |
|---|---|
| `range` (echoed in the summary) | `meta.range` on **charts** only |
| `users.newInRange` | `growth.points[].newUsers` on charts |
| `users.activeInRange` | No direct replacement. `users.activatedEver` is lifetime and monotonic; there is no per-window active-user count any more |
| `content.newPosts` / `newRecipes` / `newComments` | No replacement. Content creation over time was not one of the four charts — see §8 |
| `engagement.votesInRange` | No replacement, same reason |
| `engagement.commentsInRange` | No replacement, same reason |
| `alerts` block | Build it client-side from `moderationQueue.*` and `money.pendingStaleCount` — the thresholds are a product decision, not a data one |
| `breakdowns` block | `money` (summary) and `revenueByPlan` (charts) |
| `tables` block | Use the list endpoints (`GET /api/admin/posts`, `/payments`, `/users`) — a dashboard should not paginate |

If your UI showed a "this week's new posts" tile, that number is no longer served. Decide with the
product owner whether to drop the tile or open a follow-up to add it as a fifth chart; do not
reconstruct it by calling the list endpoints with a date filter on every dashboard load.

---

## 3. `GET /api/admin/dashboard` — the lifetime snapshot

`GET /api/admin/dashboard` · auth: `ADMIN` · no query parameters · cached 5 minutes.

Answers: *where does the platform stand right now?* Every number is a lifetime total or current
state. Nothing here changes because a week passed — only because the data did.

### 3.1 `meta`

```json
"meta": { "generatedAt": "2026-11-20T09:14:03Z", "currency": "VND" }
```

| Field | Type | What it means | How to use it |
|---|---|---|---|
| `generatedAt` | ISO-8601 timestamp | When the server built this payload — **not** when you requested it. | This is your only freshness signal. The response is cached for 5 minutes, so a warm hit can be up to 5 min old. Render "as of 09:14" and show a refresh button; do **not** display the browser's clock as the data time. |
| `currency` | string | Currency of every amount in `money`. Always `"VND"`. | Use it to pick the symbol/formatter. Do not hardcode "₫" — read this. |

**Amount scale:** all amounts are **whole dong**. VND has no minor unit, so do not divide by 100.
The payment gateway talks in hundredths, but the API does not — a `revenueTotal` of `4900000` is
4,900,000 ₫, not 49,000.00 ₫. Format with zero decimal places:
`new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 })`.

### 3.2 `users`

| Field | Type | What it means | How to use it |
|---|---|---|---|
| `total` | integer | Non-deleted accounts. | The denominator for `activationRate` and `payerConversionRate`. Headline "total members" tile. |
| `emailVerified` | integer | Accounts with `email_verified = true`. Set once — it never reverts. | Trust/health metric. Pairs with `activationRate`. |
| `activatedEver` | integer | Accounts that have ever consumed at least one AI request (`ai_usage.request_count > 0`). | **The** activation metric. Use this, not the count of users whose current status is `activated` — that status is overwritten by a suspend or deactivate, so it goes *down* and is not a growth signal. |
| `suspended` | integer | Accounts currently suspended. | Moderation health. |
| `deactivated` | integer | Accounts currently deactivated (self-service). | Churn signal. Compare against `total` for a rate. |
| `activationRate` | number 0–1 | `emailVerified / total`. `0` when there are no accounts. | Multiply by 100 for display. Already guards division by zero — do not re-guard. |

Note `suspended` and `deactivated` are **current state**, not lifetime counts. A user who was
suspended and then restored is in neither bucket.

### 3.3 `content`

| Field | Type | What it means | How to use it |
|---|---|---|---|
| `totalPosts` | integer | Lifetime non-deleted posts. | Platform scale. |
| `totalRecipes` | integer | Lifetime non-deleted recipes. | Platform scale. |
| `totalComments` | integer | Lifetime non-deleted comments. | Engagement depth. |

These are counts of existing rows, not of creations over a period. Deleting content lowers them.

### 3.4 `engagement`

| Field | Type | What it means | How to use it |
|---|---|---|---|
| `totalVotes` | integer | Lifetime votes cast (`votesUp + votesDown`). | Engagement volume. |
| `votesUp` | integer | Lifetime upvotes. | With `votesDown`, gives sentiment — which a bare total cannot. |
| `votesDown` | integer | Lifetime downvotes. | Sentiment. `votesUp / totalVotes` is a useful ratio. |
| `totalSaves` | integer | `savedPosts + savedRecipes`. | Intent-to-return signal, stronger than a vote. |
| `savedPosts` | integer | Lifetime post saves. | Which content type people keep. |
| `savedRecipes` | integer | Lifetime recipe saves. | Same. A row saves exactly one of the two, so they sum to `totalSaves` with no overlap. |

### 3.5 `moderationQueue`

| Field | Type | What it means | How to use it |
|---|---|---|---|
| `pendingPosts` | integer | Non-deleted posts with `status = pending`. | Backlog size. Same predicate as `GET /api/admin/posts?status=pending`, so the number matches that list. |
| `pendingRecipes` | integer | Recipes with `moderationStatus = PENDING`. | Backlog size. |
| `reportedComments` | integer | Non-deleted comments with `reportCount > 0`. | Backlog size. |
| `oldestPendingAgeHours` | integer | Age in whole hours of the **single oldest** item across all three queues. `0` when nothing is waiting. | The most actionable field here. Render it as urgency ("oldest item waiting 6 days"), not as a count. Three items waiting six days is worse than thirty waiting an hour — this number is what tells them apart. |
| `filterSkippedPosts` | integer | Posts with `flag = FILTER_SKIPPED` — the content filter never produced a verdict. | Items that fell through automation and need a human. Worth its own warning badge. |

`oldestPendingAgeHours` is a **minimum across all three queues**, deliberately not three separate
ages — the admin needs one "how far behind are we" number, not three to compare.

### 3.6 `money` — the headline block

| Field | Type | What it means | How to use it |
|---|---|---|---|
| `revenueTotal` | integer | Sum of `payment_ledger.amount` where `status = succeeded`, in whole dong. | The big number. **Gross, not net** — see the caveat below. |
| `transactionsTotal` | integer | Count of succeeded payments. | Volume. One member can produce several. |
| `payingUsersTotal` | integer | Distinct members with at least one succeeded payment. | Reach of monetisation. |
| `averageOrderValue` | number | `revenueTotal / transactionsTotal`. `0` when nothing succeeded. | Basket size. |
| `arpu` | number | `revenueTotal / payingUsersTotal`. `0` when nobody has paid. | **Caveat: label this "revenue per paying user" (ARPPU), not "average revenue per user".** It divides by payers, not by all accounts. Labelling it ARPU will overstate the per-member figure by the payer-conversion factor. If you want true ARPU, compute `revenueTotal / users.total` yourself. |
| `freeGrantsTotal` | integer | Acquisitions granted with `payment_id IS NULL` — allowance handed out for free. | Cost the revenue figure does not show. A rising grant count with flat revenue means you are giving away more than you sell. |
| `payerConversionRate` | number 0–1 | `payingUsersTotal / users.total`. | What share of members ever paid. Multiply by 100. |
| `pendingStaleCount` | integer | Payments still `pending` more than 24 hours after checkout. | Money that tried and never arrived. Nothing expires or reconciles these, so this pool only grows — treat a rising value as a bug to investigate, not normal churn. |

**`revenueTotal` is gross.** There is no refund write path anywhere in this backend, so no
`refunded` rows exist and nothing is subtracted. Do not label it "net revenue" or "revenue after
refunds". If a refund feature ships later, this field's meaning will change and this guide will be
updated with it.

---

## 4. `GET /api/admin/dashboard/charts` — the time-based surface

`GET /api/admin/dashboard/charts?range=day|week|month` · auth: `ADMIN` · cached 15 min per range.

Answers: *how is it moving?* Four series, all sharing one bucket size, so you can draw them against
a single x-axis.

### 4.1 Request

| Parameter | Type | Required | Default | Notes |
|---|---|---|---|---|
| `range` | string | no | `week` | One of `day`, `week`, `month`. Any other value → `400`. |

`custom` was **removed** and will return `400`. There is no arbitrary from/to. This is deliberate:
free windows would need a maximum span, a maximum bucket count, and a caller-chosen bucket size
that could desynchronise the four series. With three presets the server guarantees alignment.

### 4.2 Bucket shape — read this before building the chart

| `range` | `interval` | Buckets | Window |
|---|---|---|---|
| `day` | `hour` | 24 | last 24 hours |
| `week` | `day` | 7 | last 7 days |
| `month` | `day` | 30 | last 30 days |

`month` is the **last 30 days**, not the calendar month, so the bucket count is stable. Do not label
the axis "November" — label it with the actual `from`/`to` dates.

### 4.3 `meta`

```json
"meta": {
  "range": "week", "interval": "day",
  "from": "2026-11-14T00:00:00Z", "to": "2026-11-20T09:14:03Z",
  "boundary": "inclusiveStartExclusiveEnd",
  "currency": "VND",
  "generatedAt": "2026-11-20T09:14:03Z"
}
```

| Field | Type | What it means | How to use it |
|---|---|---|---|
| `range` | string | The preset you asked for, echoed after validation. | Read it to confirm the server honoured your request rather than trusting local state. |
| `interval` | string | Bucket size the server chose: `hour` or `day`. **Output only — never send it.** | Use it to format x-axis ticks (hourly → `HH:mm`, daily → `dd/MM`). |
| `from` | timestamp | Window start, **inclusive**. | Axis lower bound. |
| `to` | timestamp | Window end, **exclusive**. | Axis upper bound. Also the freshness signal — see below. |
| `boundary` | string | Always `inclusiveStartExclusiveEnd`. | Label the axis correctly: a bucket at `bucketStart` covers `[bucketStart, bucketStart + interval)`. |
| `currency` | string | Currency of every amount in `revenueTrend` and `revenueByPlan`. Always `"VND"`. | Formatter selection, same as the summary. |
| `generatedAt` | timestamp | When the payload was built. | On this endpoint it is **always equal to `to`** — the window ends at "now", so they are the same instant. Read either; they will never disagree. |

All bucket starts are **UTC-aligned**. Convert to local time for display, but keep the alignment in
mind: a `day` bucket starts at 00:00 UTC, which is 07:00 in Vietnam. If your users are in
Asia/Ho_Chi_Minh, a "daily" bar will not line up with their calendar day. Decide deliberately: show
UTC days and say so, or re-bucket client-side into local days (which will shift values across
buckets).

### 4.4 `revenueTrend` — money that arrived

```json
"revenueTrend": { "points": [ { "bucketStart": "2026-11-14T00:00:00Z", "amount": 245000, "transactions": 5 } ] }
```

| Field | Type | What it means | How to use it |
|---|---|---|---|
| `points[].bucketStart` | timestamp | Start of the bucket. | x value. |
| `points[].amount` | integer | Succeeded revenue in that bucket, whole dong. | y value. |
| `points[].transactions` | integer | How many payments produced that amount. | Tooltip: tells one large payment apart from several ordinary ones. Do not plot as a second y-axis — the units differ. |

`points` is **always** the full bucket count (24 / 7 / 30) — empty buckets are zero-filled, never
omitted. You can plot it directly with no gap handling.

**Draw as a line or area**, not bars: it is a continuous quantity.

### 4.5 `revenueByPlan` — which plan earned

```json
"revenueByPlan": { "items": [ { "planId": "…", "code": "PRO", "name": "Pro", "amount": 4900000, "transactions": 100, "active": true } ] }
```

| Field | Type | What it means | How to use it |
|---|---|---|---|
| `items[].planId` | uuid | Plan identifier. | Key for the row; join to `GET /api/admin/plans` if you need more plan detail. |
| `items[].code` | string | Short code, e.g. `PRO`. | Compact axis label. |
| `items[].name` | string | Display name. | Tooltip / legend. |
| `items[].amount` | integer | What was **actually charged** in the window, whole dong. | Bar length. |
| `items[].transactions` | integer | Payments that produced it. | Tooltip. |
| `items[].active` | boolean | The plan's **current** catalogue state. | Dim or badge inactive plans. A plan deactivated *after* it earned still appears here — that is deliberate, so historical revenue does not vanish. Do not filter out `active: false`. |

Already sorted by `amount` descending — do not re-sort unless you want a different order.

`amount` comes from the ledger, never from the catalogue price × count. A price change does not
rewrite past revenue.

**Draw as horizontal bars.** A handful of named categories with one measure — a pie/donut cannot
compare them honestly.

One caveat: this series needs an acquisition row to attribute a payment to a plan (the join goes
`payment_ledger → ai_subscription → ai_plan`). A succeeded payment with no acquisition row cannot
appear here. So do not assert `sum(items.amount) === sum(revenueTrend.points.amount)` in a test or a
client-side check — they come from the same window but this one has an extra join.

### 4.6 `paymentHealth` — money that did not arrive

```json
"paymentHealth": {
  "points": [ { "bucketStart": "…", "succeeded": 12, "failed": 3, "pendingFresh": 1, "pendingStale": 2 } ],
  "totals": { "succeeded": 40, "failed": 8, "pendingFresh": 2, "pendingStale": 6, "successRate": 0.8333 }
}
```

| Field | Type | What it means | How to use it |
|---|---|---|---|
| `points[].succeeded` | integer | Payments that landed, bucketed by `paid_at`. | Stack segment. |
| `points[].failed` | integer | Payments that failed, bucketed by `updated_at`. | Stack segment. |
| `points[].pendingFresh` | integer | Still pending, created less than 24 h ago. | Stack segment — normal, in-flight. |
| `points[].pendingStale` | integer | Still pending, created more than 24 h ago. | Stack segment — the worrying one. |
| `totals.succeeded` | integer | Window total succeeded. | Header stat. |
| `totals.failed` | integer | Window total failed. | Header stat. |
| `totals.pendingFresh` | integer | Window total fresh pending. | Header stat. |
| `totals.pendingStale` | integer | Window total stale pending. | Header stat. |
| `totals.successRate` | number 0–1 | `succeeded / (succeeded + failed)`. Pending is **excluded** from the denominator. | Health headline. Label it "of decided payments", because in-flight attempts are not counted either way. |

The four values **partition** the attempts that started in that bucket, which is what makes a
stacked bar honest — the segments sum to a real quantity. Draw worst-on-top (succeeded, fresh
pending, failed, stale pending) so the eye lands on the problem without reading the legend.

Two things to know:

- **`totals` is not the sum of `points`.** Totals are computed from the raw rows, not from the
  merged buckets, so a row whose bucket fell outside the series is still counted. Do not recompute
  totals by summing `points` — use `totals`.
- **`failed` is a proxy.** The ledger has no `failed_at` column, so failures are bucketed by
  `updated_at`, which is when the row was last written. That is close to the decision time but not
  exact. If you label the tooltip "failed at", soften it to "failed around".

### 4.7 `growth` — acquisition vs conversion

```json
"growth": { "points": [ { "bucketStart": "…", "newUsers": 40, "newPayingUsers": 3, "conversionRate": 0.075 } ] }
```

| Field | Type | What it means | How to use it |
|---|---|---|---|
| `points[].newUsers` | integer | Non-deleted accounts created in the bucket. | Bars. |
| `points[].newPayingUsers` | integer | **Distinct members who made a succeeded payment in the bucket.** See the caveat. | Line. |
| `points[].conversionRate` | number 0–1 | `newPayingUsers / newUsers`, `0` when nobody signed up. | Optional overlay. Very noisy per bucket — smooth it (rolling average) before drawing a trend, or omit it and show it only in the tooltip. |

Both series count **people**, so they share one axis legitimately. Draw `newUsers` as bars and
`newPayingUsers` as a line on the same scale.

**Caveat on `newPayingUsers`:** despite the name, the query counts distinct members with a succeeded
payment *in that bucket* — it does not exclude someone who already paid in an earlier bucket. So a
member who pays in week 1 and again in week 3 appears in both buckets. Treat it as "distinct payers
active in this bucket", not "newly converted this bucket", and label the legend accordingly. If you
need true first-time conversion, that requires a backend change (see §8).

---

## 5. How the numbers are computed (the logic, briefly)

Useful when a number looks wrong and you need to know whether to file a frontend or backend bug.

- **Window** is half-open, `[from, to)`. A row written exactly on a boundary belongs to the later
  window only, so adjacent windows never double-count.
- **Window start is aligned to a bucket boundary.** `from` is the start of the bucket N−1 intervals
  before the current one — that is why the bucket count is exactly 24 / 7 / 30. Subtracting a raw
  duration from "now" would land mid-bucket and produce one extra point.
- **Zero-filling:** every bucket is emitted even with no rows, so a quiet period is `0`, not a gap.
- **Revenue** = `sum(amount)` where `status = 'succeeded'`, bucketed by `paid_at`.
- **Rates** return `0` rather than `null` or `NaN` when the denominator is zero. You never have to
  guard a division on the client.
- **Counts are lifetime/current-state on the summary, window-scoped on charts.** There is no overlap
  between the two payloads; do not expect summary `money.revenueTotal` to equal
  `sum(charts.revenueTrend.points.amount)` — the first is all time, the second is your window.

---

## 6. Caching and freshness

| Endpoint | TTL | Cache key |
|---|---|---|
| `/api/admin/dashboard` | 5 min | none — **all admins share one entry** |
| `/api/admin/dashboard/charts` | 15 min | `range` — each preset cached separately |

Practical implications:

- The summary can be up to 5 minutes stale, charts up to 15. Show `meta.generatedAt` and do not
  claim live data.
- A refresh button does not necessarily produce fresh data — it may return the cached entry. If you
  need a hard refresh, that needs a backend change; do not paper over it with a client-side timer
  that re-fetches and shows a new "updated at" from the browser clock.
- Charts for different ranges are independent, so switching `week → month` may be a cache miss
  (it runs the bucketed queries) while switching back is a hit (instant). Do not treat the latency
  difference as an error.
- Two admins looking at the dashboard see the same cached summary but may see different
  `generatedAt` if one hit before a cache expiry and one after.

---

## 7. Rendering recipes

| Block | Chart | Why |
|---|---|---|
| `revenueTrend` | line / area | Continuous quantity over time |
| `revenueByPlan` | horizontal bars, sorted desc | Few named categories, one measure; a donut cannot compare them |
| `paymentHealth` | stacked bar, worst-on-top | The four values partition a real total |
| `growth` | bars (`newUsers`) + line (`newPayingUsers`) on one axis | Both count people |

Money first: if the dashboard has a hero area, `money.revenueTotal` and `revenueTrend` belong there,
not the user counts.

---

## 8. Traps and known limitations

1. **The last bucket is partial.** The window ends at "now", not at a bucket boundary — for
   `range=week` the final daily bucket may hold only a few hours. It will *always* look like a
   sudden drop. Annotate it ("in progress") or grey it out; do not read it as a decline. This is the
   single most likely source of a false "revenue is crashing" alarm.
2. **Amounts are whole dong.** Never divide by 100. Format with 0 decimals.
3. **`arpu` divides by payers, not by all users.** Label it "per paying user".
4. **`revenueTotal` is gross.** No refund path exists; nothing is netted off.
5. **`totals` ≠ sum of `points`** in `paymentHealth` — use `totals`.
6. **`newPayingUsers` is not strictly first-time.** See §4.7.
7. **UTC bucket alignment** does not match a Vietnam-local calendar day. Decide and document it.
8. **Content-over-time and engagement-over-time are gone.** If those tiles mattered, they need a
   follow-up endpoint — they were not among the four charts.
9. **`range` is a closed set.** Sending `custom`, a date, or anything else returns `400`, not a
   fallback to `week`.
10. **Bucket arrays are always full length.** Do not write "if points.length < 7, show a placeholder".

---

## 9. TypeScript types

```ts
// ---------- shared ----------
type Iso = string;

// ---------- GET /api/admin/dashboard ----------
export interface DashboardMeta {
  generatedAt: Iso;
  currency: string; // "VND"
}

export interface DashboardUsers {
  total: number;
  emailVerified: number;
  activatedEver: number;
  suspended: number;
  deactivated: number;
  activationRate: number; // 0..1
}

export interface DashboardContent {
  totalPosts: number;
  totalRecipes: number;
  totalComments: number;
}

export interface DashboardEngagement {
  totalVotes: number;
  votesUp: number;
  votesDown: number;
  totalSaves: number;
  savedPosts: number;
  savedRecipes: number;
}

export interface DashboardModerationQueue {
  pendingPosts: number;
  pendingRecipes: number;
  reportedComments: number;
  oldestPendingAgeHours: number;
  filterSkippedPosts: number;
}

export interface DashboardMoney {
  revenueTotal: number;        // whole VND, gross
  transactionsTotal: number;
  payingUsersTotal: number;
  averageOrderValue: number;
  arpu: number;                // per PAYING user, not per member
  freeGrantsTotal: number;
  payerConversionRate: number; // 0..1
  pendingStaleCount: number;
}

export interface DashboardResponse {
  meta: DashboardMeta;
  users: DashboardUsers;
  content: DashboardContent;
  engagement: DashboardEngagement;
  moderationQueue: DashboardModerationQueue;
  money: DashboardMoney;
}

// ---------- GET /api/admin/dashboard/charts ----------
export type DashboardRange = 'day' | 'week' | 'month';
export type DashboardInterval = 'hour' | 'day';

export interface DashboardChartsMeta {
  range: DashboardRange;
  interval: DashboardInterval;
  from: Iso;
  to: Iso;
  boundary: 'inclusiveStartExclusiveEnd';
  currency: string; // "VND"
  generatedAt: Iso;
}

export interface RevenueTrendPoint {
  bucketStart: Iso;
  amount: number;       // whole VND
  transactions: number;
}

export interface RevenueByPlanItem {
  planId: string;
  code: string;
  name: string;
  amount: number;       // whole VND
  transactions: number;
  active: boolean;      // current catalogue state, not window state
}

export interface PaymentHealthPoint {
  bucketStart: Iso;
  succeeded: number;
  failed: number;
  pendingFresh: number;
  pendingStale: number;
}

export interface PaymentHealthTotals {
  succeeded: number;
  failed: number;
  pendingFresh: number;
  pendingStale: number;
  successRate: number; // 0..1, pending excluded from denominator
}

export interface GrowthPoint {
  bucketStart: Iso;
  newUsers: number;
  newPayingUsers: number; // distinct payers in bucket, not strictly first-time
  conversionRate: number; // 0..1
}

export interface DashboardChartsResponse {
  meta: DashboardChartsMeta;
  revenueTrend: { points: RevenueTrendPoint[] };
  revenueByPlan: { items: RevenueByPlanItem[] };
  paymentHealth: { points: PaymentHealthPoint[]; totals: PaymentHealthTotals };
  growth: { points: GrowthPoint[] };
}
```

---

## 10. Migration checklist

- [ ] Remove `range` from the summary request; it is ignored now.
- [ ] Delete reads of `alerts`, `breakdowns`, `tables`, `users.newInRange`, `users.activeInRange`,
      `content.new*`, `engagement.*InRange`.
- [ ] Move every `InRange` tile to the charts endpoint, or drop it (§2).
- [ ] Point the revenue/money UI at `money` on the summary and `revenueTrend` on charts.
- [ ] Read `meta.currency` instead of hardcoding `₫`.
- [ ] Remove any `/100` on money amounts.
- [ ] Relabel `arpu` to "revenue per paying user".
- [ ] Relabel `revenueTotal` as gross (or just "revenue").
- [ ] Mark the last chart bucket as in-progress.
- [ ] Show `meta.generatedAt` as the data timestamp, not the browser clock.
- [ ] Handle `400` for an unsupported `range`.
- [ ] Confirm the UTC-vs-local bucket decision with design.
