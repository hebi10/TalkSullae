"""Check the real optimized release startup without connecting to Firebase."""
import re
import subprocess
import sys
import time
from pathlib import Path
from xml.etree import ElementTree as ET

sys.path.insert(0, str(Path.cwd() / "scripts"))
from dismiss_launcher_anr import dismiss_launcher_dialog

PACKAGE = "com.hebi.talksullae"
OUTPUT = Path("dex-device-output")
HIERARCHY = "/sdcard/dex03-talksullae.xml"


def adb(*args, binary=False):
    return subprocess.run(["adb", *args], check=True, capture_output=True,
                          text=not binary, timeout=40).stdout


def screen():
    adb("shell", "uiautomator", "dump", HIERARCHY)
    return adb("exec-out", "cat", HIERARCHY)


def find(label):
    for _ in range(8):
        dismiss_launcher_dialog()
        try:
            xml = screen()
            root = ET.fromstring(xml)
            parents = {child: node for node in root.iter() for child in node}
            for node in root.iter("node"):
                if node.get("package") != PACKAGE or label not in (node.get("text"), node.get("content-desc")):
                    continue
                clickable = node
                while clickable in parents and clickable.get("clickable") != "true":
                    clickable = parents[clickable]
                return xml, clickable
        except (subprocess.CalledProcessError, ET.ParseError):
            pass
        time.sleep(1)
    raise AssertionError(f"Missing app UI: {label}")


def save(label, xml):
    (OUTPUT / f"{label}.xml").write_text(xml, encoding="utf-8")
    (OUTPUT / f"{label}.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))


def main():
    xml, start = find("시작하기")
    save("01-optimized-release-startup", xml)
    bounds = tuple(map(int, re.findall(r"\d+", start.attrib["bounds"])))
    x1, y1, x2, y2 = bounds
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    find("처음 오셨나요?")
    xml, login = find("Google 계정으로 로그인")
    assert login.get("enabled") == "false", "Online sign-in was enabled without a Firebase config"
    assert "데모로 시작" not in xml, "Debug-only demo was visible in release"
    save("02-optimized-release-offline-auth-guard", xml)
    print("PASS: optimized release started; offline sign-in is disabled and debug-only demo is absent")


try:
    main()
except Exception:
    try:
        save("99-release-failure", screen())
        (OUTPUT / "logcat-on-failure.txt").write_text(adb("logcat", "-d"), encoding="utf-8")
    except Exception as failure:
        print(f"Diagnostics could not be captured: {failure}", file=sys.stderr)
    raise
