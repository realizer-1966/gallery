---
name: virtual-piano
description: Show a virtual piano to play music
metadata:
  homepage: https://github.com/google-ai-edge/gallery/tree/main/skills/featured/virtual-piano
---

# Virtual Piano

A playable, horizontally-scrolling virtual piano keyboard that uses web audio. 

## Files
- `index.html`: The local entry point that loads the script.
- `index.js`: Returns the webview URL pointing to the GitHub-hosted UI.

## Prompts / Triggers
- "Open virtual piano"
- "Play the piano"
- "I want to play piano"
- "Show me a piano keyboard"

## Instructions

Call the `run_js` tool with **ALL THREE of the following exact parameters — you MUST include all three, never omit any of them.** Omitting any one causes the tool call to fail with a parameter error:

- **skill_name**: `"virtual-piano"` — the name of this skill. ALWAYS include it, always exactly `"virtual-piano"`.
- **script_name**: `"index.html"` — the script to run. Always `"index.html"`, always exactly this.
- **data**: Set `data` to the string `""` (an empty string) — do NOT omit the `data` parameter itself.