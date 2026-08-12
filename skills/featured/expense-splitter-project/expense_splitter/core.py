"""Expense Splitter — Python reference implementation.

Mirrors the logic of the Edge AI Gallery "expense-splitter" JS skill
(scripts/index.html). Python is the source of truth; the JS translation
table lives in tests/ and the skill's index.html.

Two modes:
  - split_evenly(): total bill / tip / tax split equally among N people,
    with exact-cent distribution so the rounded shares always sum to the
    grand total.
  - settle_debts(): given what each person paid, compute per-person
    balances and a minimal set of transfers so everyone nets out.
"""

from __future__ import annotations


def format_money(amount: float, currency: str = "$") -> str:
    """Format an amount as currency with thousands separators and 2 decimals."""
    sign = "-" if amount < 0 else ""
    whole, frac = f"{abs(amount):.2f}".split(".")
    return f"{sign}{currency}{int(whole):,}.{frac}"


def distribute_remainder(total_cents: int, people: int) -> list[int]:
    """Split total_cents among `people` so shares are whole cents summing exactly to total.

    The first `total_cents % people` people pay one extra cent (standard
    "who pays the leftover penny" rule).
    """
    if not isinstance(people, int) or people < 1:
        raise ValueError("people must be a positive integer")
    if total_cents < 0:
        raise ValueError("total_cents must be non-negative")
    base, rem = divmod(total_cents, people)
    return [base + (1 if i < rem else 0) for i in range(people)]


def _round_cents(amount: float) -> int:
    return round(amount * 100)


def split_evenly(
    total: float,
    people: int,
    tip_percent: float = 0.0,
    tax_percent: float = 0.0,
) -> dict:
    """Split a bill equally.

    tip/tax are percentages of the base total. Returns rounded amounts and
    exact-cent per-person shares (sum == grand_total).
    """
    if total < 0:
        raise ValueError("total must be non-negative")
    if not isinstance(people, int) or people < 1:
        raise ValueError("people must be a positive integer")
    if tip_percent < 0 or tax_percent < 0:
        raise ValueError("tip_percent and tax_percent must be non-negative")

    tip_amount = round(total * tip_percent / 100, 2)
    tax_amount = round(total * tax_percent / 100, 2)
    grand_total = round(total + tip_amount + tax_amount, 2)
    grand_cents = _round_cents(grand_total)
    shares_cents = distribute_remainder(grand_cents, people)

    return {
        "total": round(total, 2),
        "tip_percent": tip_percent,
        "tax_percent": tax_percent,
        "tip_amount": tip_amount,
        "tax_amount": tax_amount,
        "grand_total": grand_total,
        "per_person": round(grand_cents / people) / 100,
        "shares": [c / 100 for c in shares_cents],
    }


def settle_debts(expenses: list[tuple[str, float]]) -> dict:
    """Settle group expenses.

    expenses: list of (name, amount_paid).
    balance = paid - fair_share. Positive balance = is owed money
    (creditor); negative = owes money (debtor). Returns net balances and a
    greedy minimal transfer list (debtor -> creditor).
    """
    if not expenses:
        raise ValueError("expenses must not be empty")

    total = round(sum(amount for _, amount in expenses), 2)
    n = len(expenses)
    share = round(total / n, 2)

    balances = [
        {"name": name, "balance": round(paid - share, 2)} for name, paid in expenses
    ]

    debtors = sorted(
        [dict(b) for b in balances if b["balance"] < -0.005],
        key=lambda b: b["balance"],
    )
    creditors = sorted(
        [dict(b) for b in balances if b["balance"] > 0.005],
        key=lambda b: -b["balance"],
    )

    transfers: list[dict] = []
    i = j = 0
    while i < len(debtors) and j < len(creditors):
        amount = round(min(-debtors[i]["balance"], creditors[j]["balance"]), 2)
        transfers.append(
            {"from": debtors[i]["name"], "to": creditors[j]["name"], "amount": amount}
        )
        debtors[i]["balance"] = round(debtors[i]["balance"] + amount, 2)
        creditors[j]["balance"] = round(creditors[j]["balance"] - amount, 2)
        if abs(debtors[i]["balance"]) < 0.005:
            i += 1
        if abs(creditors[j]["balance"]) < 0.005:
            j += 1

    return {
        "total": total,
        "share": share,
        "balances": balances,
        "transfers": transfers,
    }
