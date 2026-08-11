---
name: unit-converter
description: A unit converter supporting length, weight, temperature, and data-size conversions with a visual webview.
metadata:
  homepage: https://github.com/realizer-1966/gallery/tree/main/skills/featured/unit-converter
---

# Unit Converter 📏

Convert between common units of length, weight, temperature, and data size.

## Instructions

Call the `run_js` tool with the following exact parameters:

- **data**: A JSON string with these fields:
  - `value`: Number — the numeric value to convert (required).
  - `from`: String — source unit (required).
  - `to`: String — target unit (required).

### Supported Units

| Category | Units |
|---|---|
| **Length** | `mm`, `cm`, `m`, `km`, `in`, `ft`, `yd`, `mi` |
| **Weight** | `mg`, `g`, `kg`, `t`, `oz`, `lb` |
| **Temperature** | `c` (Celsius), `f` (Fahrenheit), `k` (Kelvin) |
| **Data size** | `b`, `kb`, `mb`, `gb`, `tb` (binary, base-1024) |

### Sample Commands

- "Convert 5 km to miles"
- "How many inches is 180 cm?"
- "Convert 100°F to Celsius"
- "How many MB is 2 GB?"
- "Convert 3.5 kg to pounds"
