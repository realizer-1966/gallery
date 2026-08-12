---
name: query-wikipedia
description: Query summary from Wikipedia for a given topic.
---

# Query Wiki

## Instructions

Call the `run_js` tool with **ALL THREE of the following exact parameters — you MUST include all three, never omit any of them.** Omitting any one causes the tool call to fail with a parameter error:

- **skill_name**: `"query-wikipedia"` — the name of this skill. ALWAYS include it, always exactly `"query-wikipedia"`.
- **script_name**: `"index.html"` — the script to run. Always `"index.html"`, always exactly this.
- **data**: A JSON string with the following fields. If the user gave no topic, set `data` to the string `""` (an empty string) — do NOT omit the `data` parameter itself.
- **topic**: Required. Extract ONLY the primary entity, person, or event (e.g., "2026 Oscars", "Albert Einstein"). You MUST REMOVE all specific question details, action words, or conversational text (e.g., do NOT include words like "winner", "best picture", "who won", "history of"). Search for the broad subject so the tool can return the main article.
- **lang**: Required. The 2-letter language code. This code MUST match the language of the keywords you provided in the `topic` field. Use standard codes, e.g., "en" (English), "es" (Spanish), "zh" (Chinese), "fr" (French), "de" (German), "ja" (Japanese), "ko" (Korean), "it" (Italian), "pt" (Portuguese), "ru" (Russian), "ar" (Arabic), "hi" (Hindi).