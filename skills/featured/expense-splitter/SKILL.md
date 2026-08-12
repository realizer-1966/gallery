---
name: expense-splitter
description: Splits bills and settles group expenses with tips and taxes.
metadata:
  homepage: https://github.com/realizer-1966/gallery/tree/main/skills/featured/expense-splitter
---

# Expense Splitter 💸

Splits restaurant bills and group expenses. Two modes:

- **even** — split a total bill equally among N people (with optional tip/tax percentages).
- **uneven** — each person paid a different amount; computes who owes whom and settles the group with the fewest transfers.

## Instructions

Call the `run_js` tool with the following exact parameters:

- **data**: A JSON string with these fields:
  - `action`: String — one of `"even"`, `"uneven"`. Defaults to `"even"`.
  - `total`: Number — the bill total (required for `"even"`).
  - `people`: Integer — number of people (required for `"even"`, ≥ 1).
  - `tip_percent`: Number (optional) — tip percentage of the base total (default 0).
  - `tax_percent`: Number (optional) — tax percentage of the base total (default 0).
  - `expenses`: Array of `[name, amount]` pairs — what each person paid (required for `"uneven"`). Example: `[["Alice", 30], ["Bob", 25]]`.
  - `currency`: String (optional) — currency symbol, default `"$"`.

### Examples

| Action | Data | Result |
|---|---|---|
| even | `{"total": 120, "people": 4, "tip_percent": 10}` | Bill: $120.00 + 10% tip ($12.00) = $132.00. Each of 4 pays $33.00. |
| even | `{"total": 100, "people": 3}` | 3 people, each pays $33.34 / $33.33 / $33.33 (exact-cent split). |
| uneven | `{"expenses": [["Alice", 100], ["Bob", 50]]}` | Bob pays Alice $25.00. |

### Sample Commands

- "Split $120 between 4 people with 10% tip"
- "How much does each person pay for a $85 bill with 8% tax, 3 people?"
- "We paid 30, 25, and 20 — who owes whom?"
- "Settle our dinner: Alice paid $100, Bob $50"
- "Split this bill evenly and show the calculator"

For `"even"` mode, tip and tax are percentages of the base total and are **not** compounded. The per-person shares are distributed to exact cents so the rounded shares always sum to the grand total (the first people pay any leftover penny).

For `"uneven"` mode, each person's fair share is `total / N`; a positive balance means the person is owed money, a negative balance means they owe. Always return the transfer list ("X pays Y $Z") so the user can settle up.
