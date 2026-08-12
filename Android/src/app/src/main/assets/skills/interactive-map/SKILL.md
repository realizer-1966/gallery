---
name: interactive-map
description: Show an interactive map view for the given location.
---

# Interactive map

## Examples

- "Show [a place] on interactive map"
- "Find [a place] on interactive map"

## Instructions

Call the `run_js` tool with **ALL THREE of the following exact parameters — you MUST include all three, never omit any of them.** Omitting any one causes the tool call to fail with a parameter error:

- **skill_name**: `"interactive-map"` — the name of this skill. ALWAYS include it, always exactly `"interactive-map"`.
- **script_name**: `"index.html"` — the script to run. Always `"index.html"`, always exactly this.
- **data**: A JSON string with the following field. If the user gave no location, set `data` to the string `""` (an empty string) — do NOT omit the `data` parameter itself.
  - location: The location to show on the map.
