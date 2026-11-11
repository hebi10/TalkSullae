#!/usr/bin/env python3
"""Export the Play Store screenshots produced by the Android instrumentation tests."""

import subprocess
import sys
from pathlib import Path

from capture_matrix import PACKAGE, adb


SCREENS = ("01-login", "02-rooms", "03-create", "04-approval", "05-invite", "06-members")


def export_screenshots(output):
    output.mkdir(parents=True, exist_ok=True)
    adb("shell", "wm", "size", "1080x1920")
    adb("shell", "wm", "density", "336")
    adb("shell", "settings", "put", "system", "font_scale", "1.0")
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "run-as", PACKAGE, "rm", "-rf", "files/play-store-captures")
    report = output / "instrumentation.txt"
    report.write_text(adb(
        "shell", "am", "instrument", "-w", "-r", "-e", "capturePlayStore", "true",
        "-e", "class", f"{PACKAGE}.StoreScreenshotsTest",
        f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner",
    ), encoding="utf-8")
    subprocess.run([
        sys.executable, str(Path(__file__).with_name("assert_instrumentation.py")), str(report), "2"
    ], check=True)
    for screen in SCREENS:
        filename = f"{screen}.jpg"
        screenshot = adb(
            "exec-out", "run-as", PACKAGE, "cat", f"files/play-store-captures/{filename}", binary=True
        )
        if not screenshot.startswith(b"\xff\xd8\xff") or not screenshot.endswith(b"\xff\xd9"):
            raise RuntimeError(f"Invalid Play Store JPEG: {filename}")
        (output / filename).write_bytes(screenshot)


if __name__ == "__main__":
    export_screenshots(Path(sys.argv[1]))
