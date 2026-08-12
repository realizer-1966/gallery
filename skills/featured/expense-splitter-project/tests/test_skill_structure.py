"""Structure validation tests for the expense-splitter Edge AI Gallery skill."""

import re
import pytest
from pathlib import Path

SKILL_DIR = Path(__file__).resolve().parent.parent.parent / "expense-splitter"


class TestSkillDirectory:
    def test_skill_md_exists(self):
        assert (SKILL_DIR / "SKILL.md").is_file(), "SKILL.md missing"

    def test_scripts_index_html_exists(self):
        assert (SKILL_DIR / "scripts" / "index.html").is_file(), "scripts/index.html missing"

    def test_assets_webview_html_exists(self):
        assert (SKILL_DIR / "assets" / "webview.html").is_file(), "assets/webview.html missing"


class TestSkillMd:
    @pytest.fixture(autouse=True)
    def load(self):
        self.content = (SKILL_DIR / "SKILL.md").read_text(encoding="utf-8")

    def test_has_frontmatter(self):
        assert self.content.startswith("---"), "SKILL.md must start with frontmatter"

    def test_has_name_field(self):
        m = re.search(r"^name:\s*(.+)$", self.content, re.M)
        assert m, "frontmatter missing 'name'"
        assert m.group(1).strip() == "expense-splitter", (
            f"name must be 'expense-splitter', got '{m.group(1).strip()}'"
        )

    def test_has_description_field(self):
        m = re.search(r"^description:\s*(.+)$", self.content, re.M)
        assert m, "frontmatter missing 'description'"
        assert len(m.group(1).strip()) >= 10, "description too short"
        assert len(m.group(1).strip()) <= 60, "description too long (max 60 chars)"

    def test_mentions_run_js(self):
        assert "run_js" in self.content, "SKILL.md must instruct run_js"

    def test_mentions_both_actions(self):
        for action in ["even", "uneven"]:
            assert action in self.content, f"SKILL.md must document '{action}' action"

    def test_mentions_tip_and_tax(self):
        for field in ["tip_percent", "tax_percent"]:
            assert field in self.content, f"SKILL.md must document '{field}' field"

    def test_mentions_expenses_field(self):
        assert "expenses" in self.content, "SKILL.md must document 'expenses' field"

    def test_mentions_currency_field(self):
        assert "currency" in self.content, "SKILL.md must document 'currency' field"


class TestIndexHtml:
    @pytest.fixture(autouse=True)
    def load(self):
        self.content = (SKILL_DIR / "scripts" / "index.html").read_text(encoding="utf-8")

    def test_has_entry_function(self):
        assert "ai_edge_gallery_get_result" in self.content, "index.html must define entry function"

    def test_returns_json_string(self):
        assert "JSON.stringify" in self.content, "must return JSON.stringify"

    def test_has_error_handling(self):
        assert "error" in self.content.lower(), "must handle errors"

    def test_has_try_catch(self):
        assert "try" in self.content and "catch" in self.content, "must have try/catch"

    def test_handles_even_action(self):
        assert '"even"' in self.content, "index.html must handle 'even' action"

    def test_handles_uneven_action(self):
        assert '"uneven"' in self.content, "index.html must handle 'uneven' action"

    def test_has_split_evenly_logic(self):
        assert "splitEvenly" in self.content or "distributeRemainder" in self.content, "must implement even split"

    def test_has_settle_debts_logic(self):
        assert "settleDebts" in self.content, "must implement settlement"

    def test_mirrors_exact_cent_rule(self):
        # The JS mirror must copy balance objects before mutating (the Python
        # dict-alias bug we caught in core.py). Look for the spread copy.
        assert "balances" in self.content and "map((b) => ({ ...b }))" in self.content, (
            "must copy balance objects before settling (mirror of core.py fix)"
        )

    def test_returns_webview_url(self):
        assert "webview.html" in self.content, "must return webview URL"


class TestWebviewHtml:
    @pytest.fixture(autouse=True)
    def load(self):
        self.content = (SKILL_DIR / "assets" / "webview.html").read_text(encoding="utf-8")

    def test_is_valid_html(self):
        assert "<!DOCTYPE html>" in self.content or "<!doctype html>" in self.content, "must be valid HTML"

    def test_has_mode_tabs(self):
        assert "even" in self.content and "uneven" in self.content, "must support both modes"

    def test_has_inputs(self):
        assert "<input" in self.content, "must have inputs"

    def test_has_buttons(self):
        assert "button" in self.content.lower() or "<button" in self.content, "must have buttons"

    def test_has_interaction(self):
        assert "onclick" in self.content or "oninput" in self.content, "must be interactive"

    def test_has_result_display(self):
        assert "results" in self.content or "summary" in self.content, "must have result display"

    def test_has_transfers_display(self):
        assert "transfer" in self.content, "must display settlement transfers"

    def test_dark_theme(self):
        assert "background" in self.content, "must have styling"
