---
name: restaurant-roulette
description: Show a roulette wheel to allow user to randomly select a restaurant based on location and cuisine.
metadata:
  require-secret: true
  require-secret-description: you can get api key from https://ai.google.dev/gemini-api/docs/api-key
  homepage: https://github.com/google-ai-edge/gallery/tree/main/skills/featured/restaurant-roulette
---

# Restaurant Roulette

This skill searches for up to 10 restaurants matching a specific cuisine and location in a spin wheel.

## Examples

* "Suggest Mexican food in San Jose."
* "Find a random Italian restaurant near Sunnyvale."
* "Where should I get Sushi in San Francisco today?"
* "Show a restaurant roulette for Indian food in Palo Alto."

## Instructions

Call the `run_js` tool with **ALL THREE of the following exact parameters — you MUST include all three, never omit any of them.** Omitting any one causes the tool call to fail with a parameter error:

- **skill_name**: `"restaurant-roulette"` — the name of this skill. ALWAYS include it, always exactly `"restaurant-roulette"`.
- **script_name**: `"index.html"` — the script to run. Always `"index.html"`, always exactly this.
- **data**: A JSON string with the following fields. If the user gave no details, set `data` to the string `""` (an empty string) — do NOT omit the `data` parameter itself.
  - location: the target city or location (e.g., "San Jose", "Sunnyvale", "San Francisco").
  - cuisine: the style of food or cuisine desired (e.g., "Mexican", "Italian", "Indian", "Sushi").

DO NOT use any other tool, DO NOT call `run_intent`.

IMPORTANT: When the wheel is generated, DO NOT pick a winner for the user or make up a restaurant. Simply return the requested webview and tell the user to tap the preview card to spin the wheel themselves.