"""Logic tests for the expense-splitter Python reference implementation.

Python is the source of truth for the JS mirror in the skill's
scripts/index.html. Keep the JS translation table at the bottom in sync
with these tests.
"""

import pytest

from expense_splitter.core import (
    distribute_remainder,
    format_money,
    settle_debts,
    split_evenly,
)


# ── format_money ────────────────────────────────────────────────────────

class TestFormatMoney:
    @pytest.mark.parametrize(
        "amount,currency,expected",
        [
            (0, "$", "$0.00"),
            (12, "$", "$12.00"),
            (12.5, "$", "$12.50"),
            (12.345, "$", "$12.35"),
            (1234.5, "$", "$1,234.50"),
            (1000000, "$", "$1,000,000.00"),
            (0.01, "$", "$0.01"),
            (-25, "$", "-$25.00"),
            (-1234.56, "$", "-$1,234.56"),
            (99999.99, "€", "€99,999.99"),
            (1000, "₩", "₩1,000.00"),
        ],
    )
    def test_formats(self, amount, currency, expected):
        assert format_money(amount, currency) == expected


# ── distribute_remainder ────────────────────────────────────────────────

class TestDistributeRemainder:
    @pytest.mark.parametrize(
        "total_cents,people,expected",
        [
            (100, 1, [100]),
            (10000, 4, [2500, 2500, 2500, 2500]),
            (10001, 4, [2501, 2500, 2500, 2500]),  # first person pays extra cent
            (10002, 4, [2501, 2501, 2500, 2500]),
            (10003, 4, [2501, 2501, 2501, 2500]),
            (10004, 4, [2501, 2501, 2501, 2501]),
            (1, 3, [1, 0, 0]),
            (0, 5, [0, 0, 0, 0, 0]),
            (7, 2, [4, 3]),
        ],
    )
    def test_distributes_exactly(self, total_cents, people, expected):
        shares = distribute_remainder(total_cents, people)
        assert shares == expected
        assert sum(shares) == total_cents
        assert max(shares) - min(shares) <= 1

    def test_rejects_zero_people(self):
        with pytest.raises(ValueError):
            distribute_remainder(100, 0)

    def test_rejects_negative_total(self):
        with pytest.raises(ValueError):
            distribute_remainder(-1, 2)


# ── split_evenly ────────────────────────────────────────────────────────

class TestSplitEvenly:
    @pytest.mark.parametrize(
        "total,people,tip,tax",
        [
            (100, 4, 0, 0),
            (0, 1, 0, 0),
            (99.99, 3, 0, 0),
            (120, 4, 10, 0),
            (100, 4, 0, 8),
            (50, 2, 15, 5),
            (123.45, 5, 7.5, 6.25),
            (1000000, 7, 0, 0),
            (0.03, 2, 0, 0),
        ],
    )
    def test_shares_sum_to_grand_total(self, total, people, tip, tax):
        result = split_evenly(total, people, tip, tax)
        assert sum(result["shares"]) == pytest.approx(result["grand_total"], abs=0.011)
        assert len(result["shares"]) == people
        assert result["grand_total"] >= 0

    def test_no_tip_no_tax(self):
        r = split_evenly(100, 4)
        assert r["tip_amount"] == 0
        assert r["tax_amount"] == 0
        assert r["grand_total"] == 100
        assert r["per_person"] == 25
        assert r["shares"] == [25, 25, 25, 25]

    def test_tip_percent(self):
        r = split_evenly(100, 4, tip_percent=10)
        assert r["tip_amount"] == 10
        assert r["grand_total"] == 110
        assert r["per_person"] == 27.5

    def test_tax_percent(self):
        r = split_evenly(100, 4, tax_percent=8)
        assert r["tax_amount"] == 8
        assert r["grand_total"] == 108

    def test_tip_and_tax_are_percent_of_base(self):
        r = split_evenly(100, 2, tip_percent=10, tax_percent=10)
        assert r["tip_amount"] == 10
        assert r["tax_amount"] == 10
        assert r["grand_total"] == 120  # NOT 121 (no compounding)

    def test_rounding_remainder_goes_to_first_people(self):
        # $100 / 3 → 33.33, 33.33, 33.34
        r = split_evenly(100, 3)
        assert r["shares"] == [33.34, 33.33, 33.33]
        assert sum(r["shares"]) == 100

    def test_penny_total(self):
        r = split_evenly(0.01, 2)
        assert r["shares"] == [0.01, 0.0]
        assert sum(r["shares"]) == 0.01

    def test_zero_total(self):
        r = split_evenly(0, 3)
        assert r["grand_total"] == 0
        assert r["shares"] == [0, 0, 0]

    def test_single_person(self):
        r = split_evenly(99.99, 1)
        assert r["grand_total"] == 99.99
        assert r["shares"] == [99.99]

    def test_rejects_negative_total(self):
        with pytest.raises(ValueError):
            split_evenly(-1, 2)

    def test_rejects_zero_people(self):
        with pytest.raises(ValueError):
            split_evenly(100, 0)

    def test_rejects_negative_tip(self):
        with pytest.raises(ValueError):
            split_evenly(100, 2, tip_percent=-5)

    def test_rejects_negative_tax(self):
        with pytest.raises(ValueError):
            split_evenly(100, 2, tax_percent=-1)


# ── settle_debts ────────────────────────────────────────────────────────

class TestSettleDebts:
    def test_equal_payment_no_transfers(self):
        r = settle_debts([("A", 50), ("B", 50)])
        assert r["total"] == 100
        assert r["share"] == 50
        assert r["transfers"] == []

    def test_two_people_simple(self):
        r = settle_debts([("A", 100), ("B", 50)])
        assert r["share"] == 75
        assert r["transfers"] == [{"from": "B", "to": "A", "amount": 25}]

    def test_one_pays_for_all(self):
        r = settle_debts([("A", 0), ("B", 0), ("C", 30)])
        assert r["share"] == 10
        # C is owed 20, A and B owe 10 each
        assert r["transfers"] == [
            {"from": "A", "to": "C", "amount": 10},
            {"from": "B", "to": "C", "amount": 10},
        ]

    def test_two_debtors_one_creditor_split(self):
        r = settle_debts([("A", 100), ("B", 100), ("C", 40)])
        assert r["share"] == 80
        # C owes 40 total: 20 to A, 20 to B
        assert r["transfers"] == [
            {"from": "C", "to": "A", "amount": 20},
            {"from": "C", "to": "B", "amount": 20},
        ]

    def test_three_way_cycle_collapses(self):
        # A owes 30, B is owed 30, C neutral → single transfer
        r = settle_debts([("A", 20), ("B", 80), ("C", 50)])
        assert r["transfers"] == [{"from": "A", "to": "B", "amount": 30}]

    def test_balances_sum_to_zero(self):
        r = settle_debts([("A", 12.34), ("B", 56.78), ("C", 90.12), ("D", 1.00)])
        assert sum(b["balance"] for b in r["balances"]) == pytest.approx(0, abs=0.011)
        assert r["total"] == pytest.approx(160.24, abs=0.001)

    def test_transfers_net_out(self):
        r = settle_debts([("A", 12.34), ("B", 56.78), ("C", 90.12), ("D", 1.00)])
        froms = {}
        tos = {}
        for t in r["transfers"]:
            froms[t["from"]] = froms.get(t["from"], 0) + t["amount"]
            tos[t["to"]] = tos.get(t["to"], 0) + t["amount"]
        for b in r["balances"]:
            net = tos.get(b["name"], 0) - froms.get(b["name"], 0)
            assert net == pytest.approx(b["balance"], abs=0.011)

    def test_single_person(self):
        r = settle_debts([("A", 42)])
        assert r["share"] == 42
        assert r["transfers"] == []
        assert r["balances"] == [{"name": "A", "balance": 0}]

    def test_penny_rounding_ignored(self):
        # balances under half a cent are ignored → no transfers
        r = settle_debts([("A", 10.01), ("B", 10.00), ("C", 10.00)])
        assert r["transfers"] == []

    def test_names_with_spaces_and_unicode(self):
        r = settle_debts([("김철수", 100), ("Jane Doe", 50)])
        assert r["transfers"] == [{"from": "Jane Doe", "to": "김철수", "amount": 25}]

    def test_rejects_empty_list(self):
        with pytest.raises(ValueError):
            settle_debts([])

    def test_all_zero_payments(self):
        r = settle_debts([("A", 0), ("B", 0)])
        assert r["total"] == 0
        assert r["transfers"] == []
