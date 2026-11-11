#!/usr/bin/env python3
"""Prevent emulator ANR handling from hiding app failures or clicking unsafe actions."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import call, patch
import xml.etree.ElementTree as ET

script = Path(__file__).resolve().parents[1] / "dismiss_launcher_anr.py"
spec = importlib.util.spec_from_file_location("talksullae_emulator_anr", script)
anr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(anr)


def dialog(title, buttons=()):
    root = ET.Element("hierarchy")
    ET.SubElement(root, "node", {"text": title})
    for label in buttons:
        ET.SubElement(root, "node", {"text": label, "bounds": "[20,40][220,100]"})
    return root


class EmulatorAnrSafetyTests(unittest.TestCase):
    def test_system_ui_uses_wait_not_close(self):
        root = dialog("System UI isn't responding", ("Close app", "Wait"))
        with patch.object(anr, "hierarchy", side_effect=[root, dialog("")]), \
             patch.object(anr, "adb") as adb, patch.object(anr.time, "sleep"):
            self.assertEqual(anr.dismiss_launcher_dialog(), 1)
        adb.assert_called_once_with("shell", "input", "tap", "120", "70")

    def test_launcher_uses_close(self):
        root = dialog("Quickstep isn't responding", ("Close app",))
        with patch.object(anr, "hierarchy", side_effect=[root, dialog("")]), \
             patch.object(anr, "adb") as adb, patch.object(anr.time, "sleep"):
            self.assertEqual(anr.dismiss_launcher_dialog(), 1)
        adb.assert_called_once_with("shell", "input", "tap", "120", "70")

    def test_app_anr_is_never_hidden(self):
        root = dialog("TalkSullae isn't responding", ("Close app", "Wait"))
        with patch.object(anr, "hierarchy", return_value=root), patch.object(anr, "adb") as adb:
            self.assertEqual(anr.dismiss_launcher_dialog(), 0)
        adb.assert_not_called()

    def test_system_ui_requires_wait_button(self):
        root = dialog("System UI isn't responding", ("Close app",))
        with patch.object(anr, "hierarchy", return_value=root), patch.object(anr, "adb") as adb:
            with self.assertRaisesRegex(RuntimeError, "no safe action"):
                anr.dismiss_launcher_dialog()
        adb.assert_not_called()

    def test_repeated_system_ui_anr_fails_loudly(self):
        root = dialog("System UI isn't responding", ("Wait",))
        with patch.object(anr, "hierarchy", return_value=root), \
             patch.object(anr, "adb") as adb, patch.object(anr.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "Repeated emulator"):
                anr.dismiss_launcher_dialog()
        self.assertEqual(adb.call_count, 4)


if __name__ == "__main__":
    unittest.main()
