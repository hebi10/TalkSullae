#!/usr/bin/env python3
"""Close only Android emulator launcher ANRs; never hide TalkSullae app failures."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET

LAUNCHER_TITLES = ("Quickstep isn't responding", "Pixel Launcher isn't responding", "Launcher isn't responding")
SYSTEM_UI_TITLES = ("System UI isn't responding", "시스템 UI가 응답하지 않습니다", "시스템 UI 응답 없음")
CLOSE_BUTTONS = ("Close app", "앱 닫기")
WAIT_BUTTONS = ("Wait", "대기")

def adb(*args):
    return subprocess.run(
        ["adb", *args], capture_output=True, check=True, timeout=45
    ).stdout.decode("utf-8", errors="replace")

def hierarchy():
    adb("shell", "uiautomator", "dump", "/sdcard/talksullae-system.xml")
    raw = adb("shell", "cat", "/sdcard/talksullae-system.xml")
    start, end = raw.find("<?xml"), raw.rfind("</hierarchy>")
    if start < 0 or end < 0:
        raise RuntimeError("Could not inspect emulator system dialog")
    return ET.fromstring(raw[start:end + len("</hierarchy>")])

def dialog_type(root):
    texts = [node.attrib.get("text", "") for node in root.iter()]
    if any(title in value for value in texts for title in SYSTEM_UI_TITLES):
        return "system_ui"
    if any(title in value for value in texts for title in LAUNCHER_TITLES):
        return "launcher"
    return None


def dismiss_launcher_dialog():
    """Acknowledge only emulator System UI ANRs or close launcher ANRs.

    Never dismiss an ANR originating in TalkSullae itself.
    """
    dismissed = 0
    for _ in range(4):
        root = hierarchy()
        kind = dialog_type(root)
        if kind is None:
            return dismissed
        labels = WAIT_BUTTONS if kind == "system_ui" else CLOSE_BUTTONS
        button = next((node for node in root.iter() if node.attrib.get("text", "") in labels), None)
        if button is None:
            raise RuntimeError("Recognized emulator ANR has no safe action button: " + kind)
        box = re.fullmatch(r"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]", button.attrib.get("bounds", ""))
        if box is None:
            raise RuntimeError("Emulator ANR action has no valid bounds")
        x1, y1, x2, y2 = [int(value) for value in box.groups()]
        adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
        dismissed += 1
        time.sleep(3 if kind == "system_ui" else 2)
    if dialog_type(hierarchy()) is not None:
        raise RuntimeError("Repeated emulator System UI or launcher ANR prevented screenshot capture")
    return dismissed


if __name__ == "__main__":
    print(f"Emulator system dialogs handled: {dismiss_launcher_dialog()}")
