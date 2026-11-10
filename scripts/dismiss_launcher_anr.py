#!/usr/bin/env python3
"""Close only Android emulator launcher ANRs; never hide TalkSullae app failures."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET

TITLES = ("Quickstep isn't responding", "Pixel Launcher isn't responding", "Launcher isn't responding")
BUTTONS = ("Close app", "앱 닫기")

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

def present(root):
    return any(any(title in node.attrib.get("text", "") for title in TITLES) for node in root.iter())

def dismiss_launcher_dialog():
    dismissed = 0
    for _ in range(3):
        root = hierarchy()
        if not present(root):
            return dismissed
        button = next((node for node in root.iter() if node.attrib.get("text", "") in BUTTONS), None)
        if button is None:
            raise RuntimeError("Launcher ANR has no close button")
        box = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", button.attrib.get("bounds", ""))
        if box is None:
            raise RuntimeError("Launcher close button has no bounds")
        x1, y1, x2, y2 = [int(value) for value in box.groups()]
        adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
        dismissed += 1
        time.sleep(2)
    if present(hierarchy()):
        raise RuntimeError("Repeated launcher ANR prevented screenshot capture")
    return dismissed

if __name__ == "__main__":
    print(f"Launcher ANR dialogs dismissed: {dismiss_launcher_dialog()}")
