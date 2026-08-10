"""Logic tests for Calculator."""

import math
import pytest
from calculator.core import calculate, format_result


class TestAddition:
    def test_positive(self):
        r = calculate(3, "+", 5)
        assert r == {"result": 8}

    def test_negative(self):
        r = calculate(-3, "+", 5)
        assert r == {"result": 2}

    def test_decimals(self):
        r = calculate(1.5, "+", 2.5)
        assert r == {"result": 4.0}

    def test_zero(self):
        r = calculate(0, "+", 0)
        assert r == {"result": 0}


class TestSubtraction:
    def test_positive(self):
        r = calculate(10, "-", 3)
        assert r == {"result": 7}

    def test_negative_result(self):
        r = calculate(3, "-", 10)
        assert r == {"result": -7}

    def test_decimals(self):
        r = calculate(5.5, "-", 2.3)
        assert math.isclose(r["result"], 3.2, abs_tol=1e-10)


class TestMultiplication:
    def test_positive(self):
        r = calculate(4, "*", 5)
        assert r == {"result": 20}

    def test_zero(self):
        r = calculate(100, "*", 0)
        assert r == {"result": 0}

    def test_negative(self):
        r = calculate(-3, "*", 4)
        assert r == {"result": -12}

    def test_double_negative(self):
        r = calculate(-3, "*", -4)
        assert r == {"result": 12}


class TestDivision:
    def test_exact(self):
        r = calculate(10, "/", 2)
        assert r == {"result": 5.0}

    def test_non_exact(self):
        r = calculate(10, "/", 3)
        assert math.isclose(r["result"], 3.3333333333, abs_tol=1e-9)

    def test_divide_by_zero(self):
        r = calculate(10, "/", 0)
        assert "error" in r
        assert "zero" in r["error"].lower()

    def test_zero_divided(self):
        r = calculate(0, "/", 5)
        assert r == {"result": 0.0}


class TestPower:
    def test_square(self):
        r = calculate(3, "^", 2)
        assert r == {"result": 9}

    def test_cube(self):
        r = calculate(2, "^", 3)
        assert r == {"result": 8}

    def test_zero_power(self):
        r = calculate(5, "^", 0)
        assert r == {"result": 1}

    def test_fractional(self):
        r = calculate(9, "^", 0.5)
        assert math.isclose(r["result"], 3.0, abs_tol=1e-10)


class TestSquareRoot:
    def test_perfect_square(self):
        r = calculate(16, "sqrt")
        assert r == {"result": 4.0}

    def test_non_perfect(self):
        r = calculate(2, "sqrt")
        assert math.isclose(r["result"], 1.4142135624, abs_tol=1e-9)

    def test_zero(self):
        r = calculate(0, "sqrt")
        assert r == {"result": 0.0}

    def test_negative(self):
        r = calculate(-4, "sqrt")
        assert "error" in r
        assert "negative" in r["error"].lower()


class TestPercentage:
    def test_half(self):
        r = calculate(50, "%", 100)
        assert r == {"result": 50.0}

    def test_quarter(self):
        r = calculate(25, "%", 100)
        assert r == {"result": 25.0}

    def test_over_100(self):
        r = calculate(150, "%", 100)
        assert r == {"result": 150.0}

    def test_zero_base(self):
        r = calculate(50, "%", 0)
        assert "error" in r


class TestErrors:
    def test_unknown_operator(self):
        r = calculate(5, "!", 3)
        assert "error" in r
        assert "Unknown operator" in r["error"]

    def test_missing_second_number(self):
        r = calculate(5, "+")
        assert "error" in r
        assert "two numbers" in r["error"]

    def test_sqrt_with_second_number_ignored(self):
        r = calculate(16, "sqrt", 99)
        assert r == {"result": 4.0}


class TestFormatResult:
    def test_integer(self):
        assert format_result(42.0) == "42"

    def test_decimal(self):
        assert format_result(3.14) == "3.14"

    def test_trailing_zeros(self):
        assert format_result(5.0) == "5"

    def test_negative(self):
        assert format_result(-7.0) == "-7"

    def test_small_decimal(self):
        s = format_result(0.001)
        assert s == "0.001"
