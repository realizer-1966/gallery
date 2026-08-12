---
name: expense-splitter
description: Split bills and settle group expenses (더치페이, 비용 정산, N빵) with tips and taxes.
metadata:
  homepage: https://github.com/realizer-1966/gallery/tree/main/skills/featured/expense-splitter
---

# Expense Splitter 💸

Splits restaurant bills and group expenses. Two modes:

- **even** — split a total bill equally among N people (with optional tip/tax percentages).
- **uneven** — each person paid a different amount; computes who owes whom and settles the group with the fewest transfers.

This skill matches requests about bill splitting, expense calculation, cost sharing, Dutch pay (더치페이), settling up (정산), N빵, and "how much does each person pay".

## Instructions

First use the `load_skill` tool if the skill instructions are not already in context. Then call the `run_js` tool with the following exact parameters:

- **data**: A JSON string with these fields:
  - `action`: String — one of `"even"`, `"uneven"`. Defaults to `"even"`.
  - `total`: Number — the bill total (for `"even"`). If the user didn't give a number, OMIT it — the skill uses $100 as a default.
  - `people`: Integer — number of people (for `"even"`, ≥ 1). If missing, the skill uses 4 as a default.
  - `tip_percent`: Number (optional) — tip percentage of the base total (default 0).
  - `tax_percent`: Number (optional) — tax percentage of the base total (default 0).
  - `expenses`: Array of `[name, amount]` pairs — what each person paid (for `"uneven"`). Example: `[["Alice", 30], ["Bob", 25]]`. If missing, the skill shows sample values.
  - `currency`: String (optional) — currency symbol, default `"$"`.

**IMPORTANT — if the user does not provide amounts or people, still call `run_js` with an empty JSON payload `{}` (or just the fields the user gave).** The skill never errors on missing values: it opens an interactive calculator with defaults the user can adjust. Do NOT ask for more details when numbers are missing — run the skill anyway.

### Examples

| Action | Data | Result |
|---|---|---|
| even | `{"total": 120, "people": 4, "tip_percent": 10}` | Bill: $120.00 + 10% tip ($12.00) = $132.00. Each of 4 pays $33.00. |
| even | `{"total": 100, "people": 3}` | 3 people, each pays $33.34 / $33.33 / $33.33 (exact-cent split). |
| even | `{}` | Opens the interactive calculator with defaults ($100, 4 people). |
| uneven | `{"expenses": [["Alice", 100], ["Bob", 50]]}` | Bob pays Alice $25.00. |

### Sample Commands

- "Split $120 between 4 people with 10% tip"
- "How much does each person pay for a $85 bill with 8% tax, 3 people?"
- "100달러를 4명이 더치페이해줘" (split $100 among 4)
- "밥값 정산해줘" / "비용 계산해줘" (no numbers → open the calculator)
- "We paid 30, 25, and 20 — who owes whom?"
- "Settle our dinner: Alice paid $100, Bob $50"

For `"even"` mode, tip and tax are percentages of the base total and are **not** compounded. The per-person shares are distributed to exact cents so the rounded shares always sum to the grand total (the first people pay any leftover penny).

For `"uneven"` mode, each person's fair share is `total / N`; a positive balance means the person is owed money, a negative balance means they owe. Always return the transfer list ("X pays Y $Z") so the user can settle up.
