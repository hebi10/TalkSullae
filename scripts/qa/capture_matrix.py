#!/usr/bin/env python3
"""Capture genuine TalkSullae demo screens on the currently connected Android emulator.

Images are real adb screenshots; this does not fake Google login or Firebase data.
The process fails on incomplete navigation, missing previews, ANR, or malformed PNGs.
"""
import argparse
import json
import re
import struct
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from dismiss_launcher_anr import dismiss_launcher_dialog

PACKAGE = "com.hebi.talksullae"
ACTIVITY = PACKAGE + "/.MainActivity"


def adb(*args, binary=False):
    run = subprocess.run(["adb", *args], capture_output=True, check=True, timeout=60)
    return run.stdout if binary else run.stdout.decode("utf-8", errors="replace")


def hierarchy():
    for _ in range(3):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/talksullae-2d.xml")
            data = adb("shell", "cat", "/sdcard/talksullae-2d.xml")
            start, end = data.find("<?xml"), data.rfind("</hierarchy>")
            if start >= 0 and end >= 0:
                return ET.fromstring(data[start:end + len("</hierarchy>")])
        except (subprocess.CalledProcessError, ET.ParseError):
            time.sleep(1)
    raise RuntimeError("Android accessibility UI hierarchy is unavailable")


def node_bounds(node):
    value = node.attrib.get("bounds", "")
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", value)
    return tuple(int(part) for part in match.groups()) if match else None


def click_bounds(bounds, *, horizontal_position=0.5):
    x1, y1, x2, y2 = bounds
    x = x1 + int((x2 - x1) * horizontal_position)
    adb("shell", "input", "tap", str(x), str((y1 + y2) // 2))
    time.sleep(1)


def find_by_text(root, label):
    parents = {child: parent for parent in root.iter() for child in parent}
    matching = []
    for node in root.iter():
        text = node.attrib.get("text", "")
        desc = node.attrib.get("content-desc", "")
        if text != label and desc != label and label not in (text, desc):
            continue
        current = node
        while current is not None and current.attrib.get("clickable") != "true":
            current = parents.get(current)
        target = current or node
        bounds = node_bounds(target)
        if bounds:
            matching.append((0 if text == label or desc == label else 1, bounds))
    return sorted(matching, key=lambda x: x[0])[0][1] if matching else None


def tap_label(label, *, scroll=True):
    for _ in range(7):
        dismiss_launcher_dialog()
        root = hierarchy()
        bounds = find_by_text(root, label)
        if bounds:
            click_bounds(bounds)
            return
        if scroll:
            adb("shell", "input", "swipe", "500", "1850", "500", "520", "280")
            time.sleep(0.7)
    raise RuntimeError("Required visible UI label was not found: " + label)


THEME_SWITCH_DESCRIPTION = "프로필 다크 모드 전환"


def find_profile_theme_switch(root):
    nodes = [
        node for node in root.iter()
        if node.attrib.get("content-desc") == THEME_SWITCH_DESCRIPTION
    ]
    if len(nodes) > 1:
        raise RuntimeError("Multiple profile theme switches found")
    if not nodes:
        return None
    node = nodes[0]
    if not node_bounds(node) or node.attrib.get("checked") not in ("true", "false"):
        raise RuntimeError("Profile theme switch is missing bounds or checked state")
    return node


def png_left_edge_luminance(png):
    if png[:8] != b"\x89PNG\r\n\x1a\n" or len(png) < 33:
        raise RuntimeError("Invalid PNG for theme verification")
    width, height, depth, color_type, compression, filter_method, interlace = struct.unpack(
        ">IIBBBBB", png[16:29]
    )
    if depth != 8 or color_type not in (2, 6) or interlace != 0 or compression != 0 or filter_method != 0:
        raise RuntimeError("Unsupported PNG format for theme verification")
    offset = 8
    idat = []
    while offset + 12 <= len(png):
        length = struct.unpack(">I", png[offset:offset + 4])[0]
        if offset + length + 12 > len(png):
            raise RuntimeError("Truncated screenshot PNG")
        kind = png[offset + 4:offset + 8]
        if kind == b"IDAT":
            idat.append(png[offset + 8:offset + 8 + length])
        offset += length + 12
        if kind == b"IEND":
            break
    bpp = 4 if color_type == 6 else 3
    raw = zlib.decompress(b"".join(idat))
    stride = width * bpp + 1
    if len(raw) != stride * height:
        raise RuntimeError("Unexpected PNG scanline size")
    previous = [0] * bpp
    for y in range(height // 2 + 1):
        start = y * stride
        filter_type = raw[start]
        channel = raw[start + 1:start + 1 + bpp]
        if filter_type in (0, 1):
            current = list(channel)
        elif filter_type in (2, 4):
            current = [(byte + prev) & 255 for byte, prev in zip(channel, previous)]
        elif filter_type == 3:
            current = [(byte + prev // 2) & 255 for byte, prev in zip(channel, previous)]
        else:
            raise RuntimeError("Unsupported PNG scanline filter")
        previous = current
    return sum(previous[:3]) / 3


def toggle_dark_mode():
    # A labelled switch is stable on phones/tablets; its UIAutomator checked state may lag.
    for _ in range(8):
        switch = find_profile_theme_switch(hierarchy())
        if switch is not None:
            before = png_left_edge_luminance(adb("exec-out", "screencap", "-p", binary=True))
            click_bounds(node_bounds(switch), horizontal_position=0.85)
            after = before
            for _ in range(6):
                after = png_left_edge_luminance(adb("exec-out", "screencap", "-p", binary=True))
                if abs(after - before) >= 80:
                    return
                time.sleep(0.5)
            raise RuntimeError(
                f"Profile theme background did not change: before={before:.0f}, after={after:.0f}"
            )
        time.sleep(0.5)
    raise RuntimeError("Profile theme switch was not accessible after navigation")


def size_from_png(png):
    if png[:8] != b"\x89PNG\r\n\x1a\n" or len(png) < 25:
        raise RuntimeError("Screenshot is not a complete PNG")
    return struct.unpack(">II", png[16:24])


def screenshot(folder, name, records, expected_size):
    dismiss_launcher_dialog()
    if not adb("shell", "pidof", PACKAGE).strip():
        raise RuntimeError("TalkSullae is not running for " + name)
    png = adb("exec-out", "screencap", "-p", binary=True)
    width, height = size_from_png(png)
    if (width, height) != expected_size:
        raise RuntimeError(f"Wrong capture dimensions for {name}: {width}x{height}, expected {expected_size}")
    path = folder / (name + ".png")
    path.write_bytes(png)
    records.append({"name":path.name, "width":width, "height":height, "bytes":len(png)})


def reset_and_open(font_scale):
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "pm", "clear", PACKAGE)
    adb("shell", "settings", "put", "system", "font_scale", str(font_scale))
    adb("shell", "am", "start", "-n", ACTIVITY)
    time.sleep(3)
    dismiss_launcher_dialog()


def capture_profile(folder, prefix, scale, records, size):
    reset_and_open(scale)
    screenshot(folder, prefix + "_dark_welcome", records, size)
    tap_label("시작하기")
    screenshot(folder, prefix + "_dark_onboarding", records, size)
    tap_label("데모로 시작")
    screenshot(folder, prefix + "_dark_rooms", records, size)
    tap_label("마이", scroll=False)
    screenshot(folder, prefix + "_dark_profile", records, size)
    toggle_dark_mode()
    screenshot(folder, prefix + "_light_profile", records, size)
    tap_label("내 방", scroll=False)
    screenshot(folder, prefix + "_light_rooms", records, size)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--profile", required=True)
    parser.add_argument("--size", required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    if not re.fullmatch(r"[a-z0-9_-]{3,50}", args.profile):
        raise SystemExit("Invalid QA profile")
    m = re.fullmatch(r"(\d+)x(\d+)", args.size)
    if m is None:
        raise SystemExit("Invalid target screen dimensions")
    size = tuple(map(int, m.groups()))
    report = {"profile":args.profile, "expected":list(size), "screenshots":[], "result":"started"}
    try:
        for scale, suffix in [(1.0, "100"), (2.0, "200")]:
            capture_profile(args.output, suffix, scale, report["screenshots"], size)
        if len(report["screenshots"]) != 12:
            raise RuntimeError("Expected 12 Android screenshots")
        report["result"] = "passed"
    except Exception as err:
        report["result"] = "failed"
        report["error"] = str(err)
        raise
    finally:
        (args.output / "manifest.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print(json.dumps({"profile":args.profile,"result":report["result"],
                          "captures":len(report["screenshots"]),
                          "error":report.get("error")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
