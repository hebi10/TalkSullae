"""Static TalkSullae UI overflow checks, not Android device or functional QA."""
from pathlib import Path
from playwright.sync_api import sync_playwright

HTML = Path(__file__).resolve().parents[1] / "design-prototype" / "index.html"
SCREENS = [f"S{i:02d}" for i in range(1, 25)]
SIZES = [(360, 740), (393, 852), (414, 896), (600, 820)]


def verify():
    html = HTML.read_text(encoding="utf-8")
    problems = []
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        for width, height in SIZES:
            for screen in SCREENS:
                page = browser.new_page(
                    viewport={"width": width, "height": height}, locale="ko-KR"
                )
                source = html.replace(
                    "new URLSearchParams(location.search).get('screen')||'S07'",
                    repr(screen),
                )
                page.set_content(source, wait_until="load")
                report = page.evaluate(
                    """() => {
                      const app = document.getElementById('app');
                      const body = app.querySelector('.body');
                      const foot = app.querySelector('.foot');
                      const composer = app.querySelector('.composer');
                      const buttons = [...app.querySelectorAll('.btn')];
                      const last = buttons.filter(b => body && body.contains(b)).at(-1);
                      if (body) body.scrollTop = body.scrollHeight;
                      return {
                        viewportClip: app.getBoundingClientRect().bottom > innerHeight + 2,
                        horizontalOverflow: document.documentElement.scrollWidth > innerWidth + 2,
                        footerClip: !!foot && foot.getBoundingClientRect().bottom > innerHeight - 10,
                        composerClip: !!composer && composer.getBoundingClientRect().bottom > innerHeight - 10,
                        scrollButtonClip: !!last &&
                          last.getBoundingClientRect().bottom > body.getBoundingClientRect().bottom + 2,
                        shortVisualButtons: buttons.filter(
                          b => b.getBoundingClientRect().height < 47.5
                        ).length
                      };
                    }"""
                )
                failed = {key: value for key, value in report.items() if value}
                if failed:
                    problems.append((screen, width, height, failed))
                page.close()
        browser.close()

    assert not problems, "\n".join(map(str, problems))
    count = len(SCREENS) * len(SIZES)
    print(f"PASS: {count} static screen/viewport layout checks")
    print("Not covered: real Android controls, text scale, IME, TalkBack, Firebase")


if __name__ == "__main__":
    verify()
