---
name: calculate-hash
description: Calculate the hash of a given text.
---

# Calculate hash

This skill calculates the hash of a given text.

## Examples

* "Calculate hash of..."
* "What is the hash of..."

## Instructions

Call the `run_js` tool with **ALL THREE of the following exact parameters — you MUST include all three, never omit any of them.** Omitting any one causes the tool call to fail with a parameter error:

- **skill_name**: `"calculate-hash"` — the name of this skill. ALWAYS include it, always exactly `"calculate-hash"`.
- **script_name**: `"index.html"` — the script to run. Always `"index.html"`, always exactly this.
- **data**: A JSON string with the following field. If the user gave no text, set `data` to the string `""` (an empty string) — do NOT omit the `data` parameter itself.
  - text: the text to calculate hash for