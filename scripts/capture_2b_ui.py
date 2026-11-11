#!/usr/bin/env python3
"""Capture local 2-B Compose screens from an Android emulator using UI hierarchy.

These screenshots are from a demonstration app, not evidence of real account,
invitation or backend operations. The script writes an explicit JSON report.
"""
import json
import re
import subprocess
import time
from pathlib import Path
import xml.etree.ElementTree as ET
from dismiss_launcher_anr import dismiss_launcher_dialog

OUT = Path("screenshots/2b")
OUT.mkdir(parents=True, exist_ok=True)
APP = "com.hebi.talksullae/.MainActivity"
report = {"screenshots": [], "steps": [], "error": None}


def adb(*args, data=False):
    completed = subprocess.run(
        ["adb", *args], check=True, capture_output=True,
        timeout=40
    )
    return completed.stdout if data else completed.stdout.decode("utf-8", errors="replace")


def screenshot(name):
    dismiss_launcher_dialog()
    (OUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p", data=True))
    report["screenshots"].append(f"{name}.png")


def hierarchy():
    adb("shell", "uiautomator", "dump", "/sdcard/talksullae-2b-ui.xml")
    raw = adb("shell", "cat", "/sdcard/talksullae-2b-ui.xml")
    begin, end = raw.find("<?xml"), raw.rfind("</hierarchy>")
    if begin < 0 or end < 0:
        raise RuntimeError("Android UI hierarchy XML not available")
    return ET.fromstring(raw[begin:end + len("</hierarchy>")])


def bounds(node):
    s = node.attrib.get("bounds", "")
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", s)
    return tuple(map(int, m.groups())) if m else None


def tap_text(needle):
    for attempt in range(6):
        root = hierarchy()
        parents = {child: parent for parent in root.iter() for child in parent}
        found = []
        for node in root.iter():
            text = node.attrib.get("text", "")
            desc = node.attrib.get("content-desc", "")
            if needle not in (text + " " + desc):
                continue
            clickable = node
            while clickable is not None and clickable.attrib.get("clickable") != "true":
                clickable = parents.get(clickable)
            selected = clickable or node
            b = bounds(selected)
            if b:
                found.append((0 if text == needle or desc == needle else 1,
                              (b[2] - b[0]) * (b[3] - b[1]), b))
        if found:
            _, _, (x1, y1, x2, y2) = sorted(found)[0]
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            report["steps"].append(needle)
            time.sleep(1.3)
            return
        dismiss_launcher_dialog()
        time.sleep(1.0)
    raise RuntimeError(f"Cannot find Android UI label: {needle}")


def main():
    adb("shell", "settings", "put", "system", "font_scale", "1.0")
    adb("shell", "am", "force-stop", "com.hebi.talksullae")
    adb("shell", "am", "start", "-n", APP)
    time.sleep(2.0)

    screenshot("01_welcome")
    tap_text("시작하기")
    screenshot("02_onboarding")
    tap_text("데모로 시작")
    screenshot("03_rooms")
    tap_text("새 방 만들기")
    screenshot("04_room_create")
    tap_text("방 이름")
    adb("shell", "input", "text", "Weekend")
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.8)
    tap_text("방 미리보기 생성")
    screenshot("05_created_room")
    tap_text("멤버 1명")
    screenshot("06_member_owner")
    tap_text("친구 초대 화면")
    screenshot("07_invite_owner")
    tap_text("초대받은 화면 미리보기")
    screenshot("08_invite_guest")
    tap_text("가입 요청 시연")
    screenshot("09_join_pending")


try:
    main()
except Exception as exc:
    report["error"] = str(exc)
finally:
    (OUT / "capture-report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if report["error"]:
        raise SystemExit(1)
