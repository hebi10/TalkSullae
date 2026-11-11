#!/usr/bin/env python3
"""Reject partial Android instrumentation reports, including adb exit-code zero failures."""
import re
import sys
from pathlib import Path

if len(sys.argv) != 3:
    raise SystemExit("usage: assert_instrumentation.py REPORT MIN_TEST_COUNT")
path = Path(sys.argv[1])
minimum = int(sys.argv[2])
data = path.read_text(encoding="utf-8")
successes = re.findall(r"(?m)^OK \((\d+) tests?\)\s*$", data)
if len(successes) != 1 or int(successes[0]) < minimum:
    raise SystemExit("Android instrumentation did not report all tests passing")
if re.search(r"(?mi)(FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|Tests failed)", data):
    raise SystemExit("Android instrumentation reported a failure")
print("Android instrumentation passed:", successes[0], "tests")
