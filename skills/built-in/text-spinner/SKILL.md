---
name: text-spinner
description: Spin the given text on my head.
---

## Instructions

Call the `run_js` tool with **ALL THREE of the following exact parameters — you MUST include all three, never omit any of them.** Omitting any one causes the tool call to fail with a parameter error:

- **skill_name**: `"text-spinner"` — the name of this skill. ALWAYS include it, always exactly `"text-spinner"`.
- **script_name**: `"index.html"` — the script to run. Always `"index.html"`, always exactly this.
- **data**: A JSON string with the following fields. If the user gave no text, set `data` to the string `""` (an empty string) — do NOT omit the `data` parameter itself.
  - label: The text string to spin on my head.
