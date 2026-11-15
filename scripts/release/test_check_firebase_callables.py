import unittest
from urllib.error import HTTPError

from check_firebase_callables import APP_CRITICAL_CALLABLES, unavailable_callables


class FirebaseCallableAvailabilityTests(unittest.TestCase):
    def test_checks_all_callables_without_sending_a_body_or_credentials(self):
        seen = []

        def probe(request, timeout):
            self.assertEqual(request.get_method(), "OPTIONS")
            self.assertIsNone(request.data)
            self.assertIsNone(request.get_header("Authorization"))
            seen.append(request.full_url.rsplit("/", 1)[-1])
            return 204

        self.assertEqual(unavailable_callables(probe), {})
        self.assertEqual(tuple(seen), APP_CRITICAL_CALLABLES)

    def test_reports_missing_unreachable_and_non_success_responses(self):
        def probe(request, timeout):
            name = request.full_url.rsplit("/", 1)[-1]
            if name == "saveMyProfile":
                raise HTTPError(request.full_url, 404, "Not Found", {}, None)
            if name == "previewInvitation":
                raise TimeoutError
            return 204

        self.assertEqual(unavailable_callables(probe), {
            "saveMyProfile": "HTTP 404",
            "previewInvitation": "unreachable",
        })
