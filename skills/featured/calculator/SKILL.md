---
name: calculator
description: A simple calculator that performs arithmetic operations: addition, subtraction, multiplication, division, power, square root, and percentage.
metadata:
  homepage: https://github.com/realizer-1966/gallery/tree/main/skills/featured/calculator
---

# Calculator 🧮

A simple calculator for basic arithmetic operations.

## Instructions

Call the `run_js` tool with the following exact parameters:

- **data**: A JSON string with the following fields:
  - `a`: Number — the first number (or the only number for sqrt).
  - `op`: String — the operator. One of: `"+"`, `"-"`, `"*"`, `"/"`, `"^"`, `"sqrt"`, `"%"`.
  - `b`: Number (optional) — the second number. Required for all operators except `"sqrt"`.

### Operators

| Operator | Description | Example |
|---|---|---|
| `+` | Addition | `3 + 5 = 8` |
| `-` | Subtraction | `10 - 3 = 7` |
| `*` | Multiplication | `4 * 5 = 20` |
| `/` | Division | `10 / 3 = 3.333...` |
| `^` | Power | `2 ^ 3 = 8` |
| `sqrt` | Square root | `sqrt 16 = 4` |
| `%` | Percentage | `50 % 100 = 50%` |

### Sample Commands

- "Calculate 3 + 5"
- "What is 10 divided by 3?"
- "Square root of 16"
- "2 to the power of 8"
- "What percentage is 50 of 100?"
- "Open the calculator"
