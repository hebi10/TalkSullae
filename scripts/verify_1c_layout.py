"""Static 1-C UI viewport validation. Does not verify Android/Firebase."""
from pathlib import Path
import json
import re
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / "design-prototype" / "states-1c.html").read_text(encoding="utf-8")
MATCH = re.search(r"const states=(\[.*?\]);const stateId=", SOURCE, re.S)
assert MATCH is not None, "State definitions missing"
STATES = json.loads(MATCH.group(1))
VIEWPORTS = [(360, 740), (393, 852), (414, 896), (600, 820)]


def main():
    problems = []
    total = 0
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        for width, height in VIEWPORTS:
            for state in STATES:
                page = browser.new_page(viewport={"width": width, "height": height})
                source = SOURCE.replace(
                    "new URLSearchParams(location.search).get('state')||'C01'",
                    repr(state["id"]),
                )
                page.set_content(source, wait_until="load")
                result = page.evaluate("""() => {
                    const root = document.getElementById('app');
                    const scroll = root.querySelector('.scroll');
                    if (scroll) scroll.scrollTop = scroll.scrollHeight;
                    const footer = root.querySelector('.footer');
                    const nav = root.querySelector('.gesture');
                    const note = root.querySelector('.note');
                    const buttons = [...root.querySelectorAll('.cta')];
                    return {
                        horizontalOverflow: document.documentElement.scrollWidth > innerWidth + 2,
                        footerClipped: footer.getBoundingClientRect().bottom > innerHeight - nav.getBoundingClientRect().height + 1,
                        footerOverlap: footer.getBoundingClientRect().top < scroll.getBoundingClientRect().bottom - 2,
                        buttonHeight: buttons.some(b => b.getBoundingClientRect().height < 47.5),
                        buttonTextOverflow: buttons.some(b => b.scrollWidth > b.clientWidth + 3),
                        noteClippedAfterScroll: note.getBoundingClientRect().bottom > scroll.getBoundingClientRect().bottom + 3
                    };
                }""")
                errors = [name for name, failed in result.items() if failed]
                if errors:
                    problems.append((state["id"], width, height, errors))
                total += 1
                page.close()
        browser.close()

    print(f"Verified {total} static layout states across {len(VIEWPORTS)} viewport sizes.")
    for item in problems:
        print(item)
    assert not problems, "Static UI layout failed"
    print("PASS (does not test Android, fontScale, keyboard, TalkBack, or backend)")


if __name__ == "__main__":
    main()
