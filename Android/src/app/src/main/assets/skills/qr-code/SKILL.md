---
name: qr-code
description: Generates a QR code for the given url.
---

## Instructions

Call the `run_js` tool with **ALL THREE of the following exact parameters — you MUST include all three, never omit any of them.** Omitting any one causes the tool call to fail with a parameter error:

- **skill_name**: `"qr-code"` — the name of this skill. ALWAYS include it, always exactly `"qr-code"`.
- **script_name**: `"index.html"` — the script to run. Always `"index.html"`, always exactly this.
- **data**: A JSON string with the following fields. If the user gave no URL, set `data` to the string `""` (an empty string) — do NOT omit the `data` parameter itself.
  - url: String - the url to create QR code for
