"""Calculator — arithmetic logic (Python reference implementation)."""

import math
from typing import Optional


def calculate(a: float, op: str, b: Optional[float] = None) -> dict:
    """Perform a calculation. Returns {result: value} or {error: message}."""
    if op not in ("+", "-", "*", "/", "^", "sqrt", "%"):
        return {"error": f"Unknown operator: {op}. Use +, -, *, /, ^, sqrt, %."}

    if op == "sqrt":
        if a < 0:
            return {"error": "Cannot take square root of a negative number."}
        return {"result": round(math.sqrt(a), 10)}

    if b is None:
        return {"error": f"Operator '{op}' requires two numbers."}

    if op == "+":
        return {"result": a + b}
    elif op == "-":
        return {"result": a - b}
    elif op == "*":
        return {"result": a * b}
    elif op == "/":
        if b == 0:
            return {"error": "Cannot divide by zero."}
        return {"result": a / b}
    elif op == "^":
        return {"result": a ** b}
    elif op == "%":
        if b == 0:
            return {"error": "Cannot calculate percentage with zero base."}
        return {"result": (a / b) * 100}

    return {"error": "Unknown error."}


def format_result(value: float) -> str:
    """Format a numeric result for display."""
    if isinstance(value, float):
        if value == int(value):
            return str(int(value))
        # round to 10 decimal places and strip trailing zeros
        s = f"{value:.10f}".rstrip("0").rstrip(".")
        return s
    return str(value)
