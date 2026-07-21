from pathlib import Path
import tempfile
import unittest
from urllib.error import HTTPError
from urllib.request import Request

import check


class SiteCheckSafetyTest(unittest.TestCase):
    def test_allows_only_explicit_https_github_links(self):
        self.assertIsNone(
            check.external_link_problem("https://github.com/agorokh/scenariocraft")
        )
        self.assertIsNotNone(
            check.external_link_problem("//github.com/agorokh/scenariocraft")
        )
        self.assertIsNotNone(
            check.external_link_problem("https://example.com/agorokh/scenariocraft")
        )

    def test_rejects_local_targets_outside_site_root(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            parent = Path(temporary_directory)
            root = parent / "site"
            root.mkdir()
            inside = root / "asset.png"
            inside.touch()
            outside = parent / "private.txt"
            outside.touch()

            self.assertEqual(inside.resolve(), check.contained_target(root, "asset.png"))
            self.assertIsNone(check.contained_target(root, "../private.txt"))
            self.assertIsNone(check.contained_target(root, str(outside)))

    def test_rejects_redirects_that_leave_the_allowlist(self):
        handler = check.AllowlistedRedirectHandler()
        with self.assertRaises(HTTPError) as raised:
            handler.redirect_request(
                Request("https://github.com/agorokh/scenariocraft"),
                None,
                302,
                "Found",
                {},
                "https://example.com/redirected",
            )
        raised.exception.close()


if __name__ == "__main__":
    unittest.main()
