"""Structure validation tests for the calculator Edge AI Gallery skill."""

import re
import pytest
from pathlib import Path

SKILL_DIR = Path(__file__).resolve().parent.parent.parent / "calculator"


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
        assert m.group(1).strip() == "calculator", f"name must be 'calculator', got '{m.group(1).strip()}'"

    def test_has_description_field(self):
        m = re.search(r"^description:\s*(.+)$", self.content, re.M)
        assert m, "frontmatter missing 'description'"
        assert len(m.group(1).strip()) >= 10, "description too short"

    def test_mentions_run_js(self):
        assert "run_js" in self.content, "SKILL.md must instruct run_js"

    def test_mentions_operators(self):
        for op in ["+", "-", "*", "/", "^", "sqrt", "%"]:
            assert op in self.content, f"SKILL.md must mention '{op}' operator"


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

    def test_handles_all_operators(self):
        for op in ["+", "-", "*", "/", "^", "sqrt", "%"]:
            assert op in self.content, f"index.html must handle '{op}' operator"


class TestWebviewHtml:
    @pytest.fixture(autouse=True)
    def load(self):
        self.content = (SKILL_DIR / "assets" / "webview.html").read_text(encoding="utf-8")

    def test_is_valid_html(self):
        assert "<!DOCTYPE html>" in self.content or "<!doctype html>" in self.content, "must be valid HTML"

    def test_has_buttons(self):
        assert "button" in self.content.lower(), "must have buttons"

    def test_has_display(self):
        assert "display" in self.content.lower() or "screen" in self.content.lower() or "result" in self.content.lower(), "must have result display"

    def test_has_interaction(self):
        assert "click" in self.content.lower() or "onclick" in self.content.lower(), "must be interactive"
