#!/usr/bin/env python3
"""Collect native PNGs from a dedicated test-only capture suite."""
import argparse
import datetime as dt
import hashlib
import json
import re
import struct
import subprocess
import sys
from pathlib import Path

from capture_matrix import PACKAGE, adb, dismiss_launcher_dialog

ROOT = Path(__file__).resolve().parents[2]
INVENTORY = json.loads(Path(__file__).with_name("design_capture_inventory.json").read_text(encoding="utf-8"))
PROFILE_GEOMETRY = {"phone": ("1080x2400", 420), "tablet": ("1600x2560", 240)}
CORE_IDS = {"D05","D09","D13","D14","D15","D16","D17","D27","D29","D30"}
CORE_METHODS = ("d05Rooms","d09Chat","d09CacheHidden","d13Catalog","d14Lobby",
                "d15IdentityChat","d16IdentityGuess","d17IdentityResult","d27Ranking",
                "d27Closed","d29Friend","d30Career")


def validate_png(raw, size):
    if len(raw) < 45 or raw[:8] != b"\x89PNG\r\n\x1a\n" or raw[-12:] != b"\x00\x00\x00\x00IEND\xaeB`\x82":
        raise ValueError("Incomplete PNG")
    if struct.unpack(">II", raw[16:24]) != size:
        raise ValueError("Unexpected native screenshot size")


def validate_guard(raw, name):
    guard = json.loads(raw)
    if not isinstance(guard, dict):
        raise ValueError("Capture foreground guard is not an object")
    if (guard.get("guardVersion") != 1 or guard.get("screen") != name
            or guard.get("package") != PACKAGE
            or guard.get("preForegroundClear") is not True
            or guard.get("postForegroundClear") is not True):
        raise ValueError("Capture lacks verified foreground checks")
    recoveries = guard.get("systemDialogRecoveries")
    if type(recoveries) is not int or not 0 <= recoveries <= 3:
        raise ValueError("Invalid emulator dialog recovery count")
    return guard


def source_version():
    text = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    code = re.search(r'versionCode\s*=\s*(\d+)', text)
    if not name or not code:
        raise ValueError("Missing source version")
    return name[1], int(code[1])


def run(args):
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    if sha != args.expected_sha or not re.fullmatch(r"[a-f0-9]{40}", sha):
        raise ValueError("Capture source SHA mismatch")
    size = tuple(int(v) for v in args.size.split("x"))
    version, code = source_version()
    args.output.mkdir(parents=True, exist_ok=True)
    manifest = {"schema":1,"sourceSha":sha,"versionName":version,"versionCode":code,
        "captureKind":"native-android-screenshot-with-test-data","productionVerified":False,
        "profile":args.profile,"size":list(size),"density":args.density,
        "fontScale":args.font_scale,"theme":args.theme,
        "timestampUtc":dt.datetime.now(dt.timezone.utc).isoformat(),"captures":[],"result":"started",
        "missing":[]}
    items=[item for item in INVENTORY["captures"] if not args.core_only or item["name"][:3] in CORE_IDS]
    expected={item["name"]+".png" for item in items}
    manifest["scope"]="core-10" if args.core_only else "all-34"
    manifest["missing"]=sorted(expected)
    manifest["invalid"]=[]
    manifest["foregroundGuardVersion"]=1
    manifest["preflightSystemDialogRecoveries"]=None
    try:
        adb("shell","wm","size",args.size)
        adb("shell","wm","density",str(args.density))
        adb("shell","settings","put","system","font_scale",str(args.font_scale))
        adb("shell","am","force-stop",PACKAGE)
        adb("shell","pm","clear",PACKAGE)
        # Reuse the existing emulator-only policy: System UI Wait, launcher Close.
        # An ANR from TalkSullae is not acknowledged here or by the capture guard.
        manifest["preflightSystemDialogRecoveries"]=dismiss_launcher_dialog()
        # Instrumentation can exceed the short adb helper timeout.
        classes = (",".join(f"{PACKAGE}.DesignScreensCaptureTest#{method}" for method in CORE_METHODS)
                   if args.core_only else f"{PACKAGE}.DesignScreensCaptureTest,{PACKAGE}.DesignNavigationCaptureTest")
        cmd=["adb","shell","am","instrument","-w","-r","-e","captureDesign","true","-e","designTheme",args.theme,
             "-e","class",classes,f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"]
        report=args.output/"instrumentation.txt"
        instrument_failed=False
        try:
            result=subprocess.run(cmd,capture_output=True,timeout=1500)
            report.write_bytes(result.stdout+result.stderr)
            instrument_failed=result.returncode!=0
        except subprocess.TimeoutExpired as err:
            report.write_bytes((err.stdout or b"")+(err.stderr or b""))
            instrument_failed=True
        # Preserve verified partial evidence even if a test or another page fails.
        try:
            actual=set(adb("shell","run-as",PACKAGE,"ls","files/design-captures").split())
        except (subprocess.SubprocessError,OSError):
            actual=set()
        manifest["missing"]=sorted(expected-actual)
        manifest["unexpected"]=sorted(actual-expected)
        for item in items:
            name=item["name"]+".png"
            if name not in actual:
                continue
            try:
                raw=adb("exec-out","run-as",PACKAGE,"cat","files/design-captures/"+name,binary=True)
                validate_png(raw,size)
                guard_raw=adb("exec-out","run-as",PACKAGE,"cat","files/design-capture-guards/"+item["name"]+".json",binary=True)
                guard=validate_guard(guard_raw,item["name"])
            except (subprocess.SubprocessError,OSError,ValueError):
                manifest["invalid"].append(name)
                continue
            (args.output/name).write_bytes(raw)
            manifest["captures"].append({**item,"file":name,"bytes":len(raw),"sha256":hashlib.sha256(raw).hexdigest(),
                "screenId":item["name"][:3],"theme":"app-default-demo" if item["dataType"]=="demo-navigation" else args.theme,
                "foregroundGuard":guard})
        if instrument_failed:
            raise RuntimeError("Instrumentation command failed")
        subprocess.run([sys.executable,str(Path(__file__).with_name("assert_instrumentation.py")),str(report),str(len(CORE_METHODS) if args.core_only else 34)],check=True)
        if actual!=expected or manifest["invalid"]:
            raise RuntimeError("Capture set does not match inventory")
        manifest["result"]="passed"
    except Exception:
        manifest["result"]="failed"
        raise
    finally:
        (args.output/"manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")


def parse_args(argv=None):
    parser=argparse.ArgumentParser()
    parser.add_argument("--output",type=Path,required=True)
    parser.add_argument("--expected-sha",required=True)
    parser.add_argument("--profile",choices=tuple(PROFILE_GEOMETRY),required=True)
    parser.add_argument("--size")
    parser.add_argument("--density",type=int)
    parser.add_argument("--font-scale",type=float,choices=[1.0,2.0],default=1.0)
    parser.add_argument("--theme",choices=["dark","light"],default="dark")
    parser.add_argument("--core-only",action="store_true",help="Capture only the ten redesigned screens and their fixture checkpoints")
    args=parser.parse_args(argv)
    if (args.size is None) != (args.density is None):
        parser.error("--size and --density must be supplied together")
    if args.size is None:
        args.size, args.density = PROFILE_GEOMETRY[args.profile]
    return args


if __name__=="__main__":
    run(parse_args())
