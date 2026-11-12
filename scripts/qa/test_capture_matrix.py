import struct
import unittest
import xml.etree.ElementTree as ET
import zlib
from unittest.mock import patch

import capture_matrix


def theme_tree(checked="false", *, extra_checkbox=False, duplicate=False):
    extra = '<node class="android.view.View" checkable="true" checked="true" bounds="[20,20][80,80]" />' if extra_checkbox else ""
    repeated = (
        f'<node content-desc="프로필 다크 모드 전환" checked="{checked}" '
        'checkable="true" bounds="[500,100][650,180]" />'
    )
    return ET.fromstring(f"<hierarchy>{extra}{repeated}{repeated if duplicate else ''}</hierarchy>")


def solid_rgba_png(rgb, *, filter_mode=0):
    pixel = bytes((*rgb, 255))
    if filter_mode == 0:
        raw = (b"\x00" + pixel * 4) * 4
    elif filter_mode == 2:
        raw = b"\x02" + pixel * 4 + (b"\x02" + b"\x00" * 16) * 3
    else:
        raise ValueError("Unsupported test filter")
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    ihdr = struct.pack(">IIBBBBB", 4, 4, 8, 6, 0, 0, 0)
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(raw))
        + chunk(b"IEND", b"")
    )


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

    def test_reads_actual_dark_and_light_png_background(self):
        self.assertLess(capture_matrix.png_left_edge_luminance(solid_rgba_png((16, 17, 20))), 30)
        self.assertGreater(capture_matrix.png_left_edge_luminance(solid_rgba_png((247, 247, 248), filter_mode=2)), 240)

    def test_rejects_invalid_screenshot_png(self):
        with self.assertRaisesRegex(RuntimeError, "Invalid PNG"):
            capture_matrix.png_left_edge_luminance(b"not-a-png")

    def test_toggle_checks_visual_theme_even_when_accessibility_state_stays_stale(self):
        with (
            patch.object(capture_matrix, "hierarchy", return_value=theme_tree(checked="false", extra_checkbox=True)),
            patch.object(capture_matrix, "adb", side_effect=[
                solid_rgba_png((16, 17, 20)),
                solid_rgba_png((247, 247, 248)),
            ]),
            patch.object(capture_matrix, "click_bounds") as click,
            patch.object(capture_matrix.time, "sleep"),
        ):
            capture_matrix.toggle_dark_mode()
        click.assert_called_once_with((500, 100, 650, 180), horizontal_position=0.85)

    def test_toggle_fails_if_background_never_changes(self):
        with (
            patch.object(capture_matrix, "hierarchy", return_value=theme_tree()),
            patch.object(capture_matrix, "adb", return_value=solid_rgba_png((16, 17, 20))),
            patch.object(capture_matrix, "click_bounds") as click,
            patch.object(capture_matrix.time, "sleep"),
        ):
            with self.assertRaisesRegex(RuntimeError, "background did not change"):
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
