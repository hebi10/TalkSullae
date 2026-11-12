import unittest
import xml.etree.ElementTree as ET
from unittest.mock import patch

import capture_matrix


def theme_tree(checked="false", *, extra_checkbox=False, duplicate=False):
    extra = '<node class="android.view.View" checkable="true" checked="true" bounds="[20,20][80,80]" />' if extra_checkbox else ""
    repeated = (
        f'<node content-desc="프로필 다크 모드 전환" checked="{checked}" '
        'checkable="true" bounds="[500,100][650,180]" />'
    )
    return ET.fromstring(f"<hierarchy>{extra}{repeated}{repeated if duplicate else ''}</hierarchy>")


class ProfileThemeCaptureTest(unittest.TestCase):
    def test_selects_labelled_theme_switch_even_with_other_checkboxes(self):
        node = capture_matrix.find_profile_theme_switch(theme_tree(extra_checkbox=True))
        self.assertEqual(node.attrib["content-desc"], "프로필 다크 모드 전환")
        self.assertEqual(capture_matrix.node_bounds(node), (500, 100, 650, 180))

    def test_refuses_ambiguous_theme_switches(self):
        with self.assertRaisesRegex(RuntimeError, "Multiple profile theme switches"):
            capture_matrix.find_profile_theme_switch(theme_tree(duplicate=True))

    def test_rejects_unreadable_checked_state(self):
        with self.assertRaisesRegex(RuntimeError, "missing bounds or checked state"):
            capture_matrix.find_profile_theme_switch(theme_tree(checked="unknown"))

    def test_toggle_checks_android_accessibility_state_transition(self):
        with (
            patch.object(capture_matrix, "hierarchy", side_effect=[
                theme_tree(checked="false", extra_checkbox=True),
                theme_tree(checked="true", extra_checkbox=True),
            ]),
            patch.object(capture_matrix, "click_bounds") as click,
            patch.object(capture_matrix.time, "sleep"),
        ):
            capture_matrix.toggle_dark_mode()
        click.assert_called_once_with((500, 100, 650, 180))

    def test_toggle_fails_if_switch_never_changes(self):
        with (
            patch.object(capture_matrix, "hierarchy", return_value=theme_tree(checked="false")),
            patch.object(capture_matrix, "click_bounds") as click,
            patch.object(capture_matrix.time, "sleep"),
        ):
            with self.assertRaisesRegex(RuntimeError, "did not change checked state"):
                capture_matrix.toggle_dark_mode()
        click.assert_called_once()

    def test_toggle_fails_if_profile_switch_never_appears(self):
        with (
            patch.object(capture_matrix, "hierarchy", return_value=ET.fromstring("<hierarchy />")),
            patch.object(capture_matrix, "click_bounds") as click,
            patch.object(capture_matrix.time, "sleep"),
        ):
            with self.assertRaisesRegex(RuntimeError, "not accessible"):
                capture_matrix.toggle_dark_mode()
        click.assert_not_called()


if __name__ == "__main__":
    unittest.main()
