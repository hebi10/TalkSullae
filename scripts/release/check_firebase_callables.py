"""Check routing to the application's critical production Firebase callables."""

from urllib.error import HTTPError
from urllib.request import HTTPRedirectHandler, Request, build_opener

from verify_aab import PROJECT

APP_CRITICAL_CALLABLES = (
    "createRoom",
    "saveMyProfile",
    "previewInvitation",
    "getMyCareer",
    "registerPushToken",
)


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None


def http_status(request, timeout):
    with build_opener(NoRedirect()).open(request, timeout=timeout) as response:
        return response.status


def unavailable_callables(probe=http_status):
    failures = {}
    for name in APP_CRITICAL_CALLABLES:
        request = Request(
            f"https://asia-northeast3-{PROJECT}.cloudfunctions.net/{name}",
            method="OPTIONS",
            headers={
                "Origin": "https://github.com",
                "Access-Control-Request-Method": "POST",
                "Access-Control-Request-Headers": "content-type",
            },
        )
        try:
            status = probe(request, timeout=10)
        except HTTPError as error:
            status = error.code
        except OSError:
            failures[name] = "unreachable"
            continue
        if not 200 <= status < 300:
            failures[name] = f"HTTP {status}"
    return failures


if __name__ == "__main__":
    failures = unavailable_callables()
    for name, status in failures.items():
        print(f"{name}: {status}", flush=True)
    if failures:
        raise SystemExit("Production Firebase callable routing check failed")
    print("Production Firebase callable routing returned successful preflight responses")
