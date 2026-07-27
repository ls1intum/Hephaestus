---
id: llm-cost-vocabulary
sidebar_position: 4
title: LLM cost vocabulary
description: The words the AI cost surfaces use for price, cost, spend, budget and cap — and which code owns each one.
---

# LLM cost vocabulary

Money surfaces go wrong in a particular way: two screens use one word for two different numbers, or
two words for one number, and nobody notices because both render fine. This page is the vocabulary the
LLM cost and pricing surfaces enforce, written down so a citation can resolve to something.

Rules are numbered and referenced by number from code (`glossary rule #2`). **Renumbering breaks those
citations — append, do not reorder.** Every rule below describes what the code does today; where a
rule has a single owner in code, that owner is named, and the rule belongs there rather than at each
call site.

---

## Rule 1 — Price, cost, and spend are three different numbers

| Word | What it is | Wire field | Formatter |
| --- | --- | --- | --- |
| **price** (or **rate**) | A published per-1M-token rate, as the provider lists it | `per1mInputUsd`, `per1mOutputUsd` | `formatRateUsd` |
| **cost** | What one recorded thing cost — a call, a `llm_usage_event`, a job, a turn | `costUsd`, `totalCostUsd` | `formatCostUsd` |
| **spend** | Cost summed over a window, usually a month, usually a workspace | `spentUsd`, and the totals in a usage report | `formatCostUsd` |

A price is what you would be charged; cost and spend are what you *were* charged. Never use one word
for another number — in copy, in a field name, or in a test name. "Spend" is the word for the summed
figure in user-facing copy; "cost" belongs to a single recorded item.

Prices are frozen per event: the ledger (`llm_usage_event`) stores the rates that were applied, so a
price change never rewrites history. See [ADR 0026 — one pricing
authority](https://github.com/ls1intum/Hephaestus/blob/main/docs/decisions/0026-per-purpose-agent-bindings-and-llm-governance.md).

## Rule 2 — There are two caps, they are different people's money, and they are never summed

A workspace can spend under two independent caps:

| | **Shared-model budget** | **Provider cap** |
| --- | --- | --- |
| Whose money | The **host's** — the instance pays the provider | The **workspace's** — its own provider bills it directly |
| Who sets it | Instance admin | Workspace admin |
| Who can lift it | Instance admin only | The workspace admin themselves |
| Funding source | `FundingSource.INSTANCE` | `FundingSource.WORKSPACE` |

They pause **independently**: an exhausted shared-model budget must never stop work a workspace is
paying for out of its own pocket. So there is no combined figure, no combined meter, and no "total
spend" across the two — a sum of the two would be a number nobody owes.

Every banner names *whose* cap tripped and routes to whoever can lift it. Where both are paused, the
provider cap comes first, because that is the one the reader can act on.

## Rule 3 — In user-facing copy the host's is a *budget*, the workspace's own is a *cap*

"Shared-model budget" and "provider cap" are the words that reach the screen — including the
accessible names of the meters ("Shared-model budget used", "Provider cap used by Acme").

The wire is not consistent with this and does not need to be: both are `…BudgetUsd`
(`monthlyBudgetUsd`, `ownProviderMonthlyBudgetUsd`). **The UI words are the contract; the field names
are history.** Do not rename copy to match a field.

## Rule 4 — Never render a pricing or budget enum

`PRICED` / `NO_CHARGE` / `UNPRICED` and `WITHIN` / `EXHAUSTED` / `UNVERIFIABLE` are internal states.
None of those words appears on screen.

For price, `webapp/src/lib/llm-pricing.ts#priceLabel` is the **only** place the word choice lives, and
it varies by audience:

- `PRICED` → the number itself, never the word ("$0.075 input · $0.30 output / 1M tokens")
- `NO_CHARGE` → "No metered API cost"
- `UNPRICED` → "No price set" to an instance admin, "Price not set" to a workspace admin

The price radio (`PriceModeEditor`) labels its options through the same function, so the option a
price was chosen on and the label the tables print for it cannot drift.

For budget state, the copy says what happens ("paused", "resumes"), not which enum constant produced
it. `UNVERIFIABLE` pauses a **capped** purse exactly like `EXHAUSTED` — a cap you cannot verify is not
a cap — and is a data-quality note on an uncapped one.

## Rule 5 — The formatter follows the noun, not the widget

`webapp/src/lib/money.ts` owns USD rendering, and the choice is not cosmetic:

- **`formatRateUsd`** — prices and per-unit rates. Up to four decimals, never floored: `$0.075 / 1M`
  is a real price, and this is the one number an admin checks against their provider's price list.
  Rendering it with the spend formatter would print `$0.08`, and `$0.003` would become `<$0.01`.
- **`formatCostUsd`** — anything actually spent. `$0` for nothing (not `$0.00`, which buries the
  difference between "none" and "almost none"), `<$0.01` for a nonzero amount too small for cents,
  plain cents otherwise.
- **`formatCapUsd`** — a cap someone typed, rendered the way they typed it: `$50`, not `$50.00`.

`—` is the rendering for absent in all three.

## Rule 6 — The client formats money; it does not do arithmetic on it

Amounts are exact decimals on the server (`NUMERIC`, `BigDecimal`) and land in JavaScript as binary64.
Totals, remaining budget, pace projections and cap verdicts are all computed exactly on the server and
shipped as their own fields. Re-deriving one by adding up rendered rows trades an exact number for an
approximate one, and can only ever disagree with the figure printed above it.

## Rule 7 — Say what the bound is, not how it feels

Where an effect is not immediate, copy states the bound the system actually keeps rather than hedging.
Saving a cap says "New calls resume **within a minute**" because `ProxyBudgetGate` caches its verdict
for 30 seconds — "resumes now" would be a small lie and "about a minute" a hedge. See
[ADR 0027](https://github.com/ls1intum/Hephaestus/blob/main/docs/decisions/0027-dialog-lifetime-and-where-a-write-outcome-lands.md)
for where the confirmation itself is allowed to appear.

## Rule 8 — A cap is monthly and it is not scoped to the month you are looking at

The usage page has a month stepper; the caps do not move with it. A cap is the workspace's current
setting, so the editors are reachable on the current month only, and a past month shows what the cap
*was* being judged against, not something you can edit from there.

---

## Where the words are enforced

| Concern | Owner |
| --- | --- |
| Price wording (rules 1, 4) | `webapp/src/lib/llm-pricing.ts` |
| USD rendering (rules 1, 5) | `webapp/src/lib/money.ts` |
| Which purse, and whether it pauses (rules 2, 4) | `LlmBudgetVerdict`, `FundingSource`, `LlmBudgetService` |
| The in-flight bound behind rule 7's "within a minute" | [ADR 0026](https://github.com/ls1intum/Hephaestus/blob/main/docs/decisions/0026-per-purpose-agent-bindings-and-llm-governance.md) |
