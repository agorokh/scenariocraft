#!/usr/bin/env python3
"""Validate the self-contained How to Play page and its public navigation links."""

from __future__ import annotations

import argparse
import hashlib
from html.parser import HTMLParser
from pathlib import Path
import re
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
SITE_ROOT = REPOSITORY_ROOT / "site"
INDEX = SITE_ROOT / "index.html"
STYLES = SITE_ROOT / "styles.css"
ALLOWED_LINK_HOSTS = {"github.com"}
REMOTE_RESOURCE_ATTRIBUTES = {"src", "srcset", "poster"}
NAVIGATION_HREF_TAGS = {"a", "area"}
PROTECTED_TEXT = (
    "name & logo by our 10-year-old designer, working with ChatGPT",
    "NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.",
    "no game-client captures",
)
QUICKSTART_TEXT = (
    "git clone https://github.com/agorokh/scenariocraft.git && cd scenariocraft",
    "export OPENAI_API_KEY='<your OpenAI API key>'",
    "make family-up",
    "make family-status",
    "Java 1.21.x: join localhost:25565",
    "Bedrock on an iPad, phone, or computer: add the Docker host's LAN IP with port 19132",
    "Xbox with a macOS host on the same LAN: open the Friends tab and join ScenarioCraft family demo when it appears under LAN Games.",
    "Run /speedbuild start in chat. /battle and /bb remain available for existing servers.",
)


class PageParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.ids: list[str] = []
        self.links: list[str] = []
        self.local_resources: list[str] = []
        self.remote_resources: list[str] = []
        self.stylesheets: list[str] = []
        self.image_alts: list[str | None] = []
        self.step_count = 0
        self.ingredient_count = 0
        self.tooltip_count = 0
        self.text: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        if identifier := values.get("id"):
            self.ids.append(identifier)
        classes = set((values.get("class") or "").split())
        if tag == "article" and "step" in classes:
            self.step_count += 1
        if tag == "details" and "ingredient" in classes:
            self.ingredient_count += 1
        if "item-tooltip" in classes:
            self.tooltip_count += 1
        if tag == "img":
            self.image_alts.append(values.get("alt"))
        if href := values.get("href"):
            if tag == "link" and "stylesheet" in (values.get("rel") or "").split():
                self.stylesheets.append(href)
            if tag in NAVIGATION_HREF_TAGS:
                self.links.append(href)
            elif is_remote(href):
                self.remote_resources.append(href)
            else:
                self.local_resources.append(href)
        for attribute in REMOTE_RESOURCE_ATTRIBUTES:
            if value := values.get(attribute):
                if is_remote(value):
                    self.remote_resources.append(value)
                else:
                    self.local_resources.append(value)

    def handle_data(self, data: str) -> None:
        self.text.append(data)


def is_remote(value: str) -> bool:
    return urlparse(value).scheme in {"http", "https"} or value.startswith("//")


def external_link_problem(value: str) -> str | None:
    parsed = urlparse(value)
    if value.startswith("//") or (parsed.netloc and not parsed.scheme):
        return "protocol-relative links are not allowed"
    if parsed.scheme not in {"http", "https"}:
        return f"unsupported external-link scheme: {parsed.scheme or '<none>'}"
    if parsed.hostname not in ALLOWED_LINK_HOSTS:
        return f"external link host is not allowlisted: {parsed.hostname or '<none>'}"
    return None


def contained_target(root: Path, value: str) -> Path | None:
    root = root.resolve()
    target = (root / urlparse(value).path).resolve()
    if target == root or root in target.parents:
        return target
    return None


class AllowlistedRedirectHandler(HTTPRedirectHandler):
    def redirect_request(self, request, file_pointer, code, message, headers, new_url):
        problem = external_link_problem(new_url)
        if problem:
            raise HTTPError(new_url, code, f"redirect rejected: {problem}", headers, None)
        return super().redirect_request(
            request, file_pointer, code, message, headers, new_url
        )


def normalize_text(value: str) -> str:
    return " ".join(value.split())


def versioned_asset_href(relative_path: str, asset_path: Path) -> str:
    digest = hashlib.sha256(asset_path.read_bytes()).hexdigest()[:12]
    return f"{relative_path}?v={digest}"


def stylesheet_version_problem(
    stylesheet_hrefs: list[str], styles_path: Path = STYLES
) -> str | None:
    expected = versioned_asset_href("styles.css", styles_path)
    if stylesheet_hrefs != [expected]:
        return (
            "stylesheet URL must match its content version "
            f"(expected {expected!r}, found {stylesheet_hrefs!r})"
        )
    return None


def validate_page() -> tuple[list[str], list[str]]:
    errors: list[str] = []
    html = INDEX.read_text(encoding="utf-8")
    css = STYLES.read_text(encoding="utf-8")
    readme = (REPOSITORY_ROOT / "README.md").read_text(encoding="utf-8")
    readme_text = normalize_text(readme.replace("`", "").replace("  \n", " "))
    parser = PageParser()
    parser.feed(html)
    page_text = normalize_text(" ".join(parser.text))

    if parser.step_count != 7:
        errors.append(f"expected 7 story steps, found {parser.step_count}")
    if parser.ingredient_count != 9 or parser.tooltip_count != 9:
        errors.append(
            "crafting recipe must contain exactly 9 ingredients with 9 tooltips "
            f"(found {parser.ingredient_count} and {parser.tooltip_count})"
        )
    duplicates = sorted({identifier for identifier in parser.ids if parser.ids.count(identifier) > 1})
    if duplicates:
        errors.append(f"duplicate HTML ids: {', '.join(duplicates)}")
    for href in parser.links:
        parsed = urlparse(href)
        if href.startswith("#"):
            if href[1:] not in parser.ids:
                errors.append(f"missing local anchor target: {href}")
        elif parsed.scheme or parsed.netloc or href.startswith("//"):
            if problem := external_link_problem(href):
                errors.append(f"invalid external link {href!r}: {problem}")
        else:
            target = contained_target(SITE_ROOT, href)
            if target is None:
                errors.append(f"local link escapes site directory: {href}")
            elif not target.exists():
                errors.append(f"missing local link target: {href}")
    for resource in parser.local_resources:
        target = contained_target(SITE_ROOT, resource)
        if target is None:
            errors.append(f"local resource escapes site directory: {resource}")
        elif not target.is_file():
            errors.append(f"missing local resource: {resource}")
    if parser.remote_resources:
        errors.append(f"page would request remote resources: {parser.remote_resources}")
    if problem := stylesheet_version_problem(parser.stylesheets):
        errors.append(problem)
    if any(not alt or not alt.strip() for alt in parser.image_alts):
        errors.append("every image must have non-empty alt text")
    if re.search(r"@import|url\([^)]*(?:https?:)?//", css, re.IGNORECASE):
        errors.append("CSS must not import or request remote resources")
    for protected in PROTECTED_TEXT:
        if protected not in page_text:
            errors.append(f"protected text changed or disappeared: {protected}")
    for required in ("ScenarioCraft", "Speed Build", "OpenAI Build Week"):
        head = html.split("</head>", 1)[0]
        if required not in head:
            errors.append(f"page metadata is missing {required!r}")
    for snippet in QUICKSTART_TEXT:
        if snippet not in page_text:
            errors.append(f"page quickstart is missing canonical text: {snippet}")
        if snippet not in readme_text:
            errors.append(f"README quickstart is missing canonical text: {snippet}")
    return errors, parser.links


def check_external_links(links: list[str]) -> list[str]:
    errors: list[str] = []
    opener = build_opener(AllowlistedRedirectHandler())
    for link in sorted({link for link in links if is_remote(link)}):
        if problem := external_link_problem(link):
            errors.append(f"external link was not fetched: {link}: {problem}")
            continue
        request = Request(link, headers={"User-Agent": "ScenarioCraft-site-check/1.0"})
        try:
            with opener.open(request, timeout=20) as response:
                if response.status >= 400:
                    errors.append(f"external link returned {response.status}: {link}")
        except HTTPError as error:
            errors.append(f"external link returned {error.code}: {link}")
        except URLError as error:
            errors.append(f"external link failed: {link}: {error.reason}")
        except ValueError as error:
            errors.append(f"external link was invalid: {link}: {error}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check-external", action="store_true")
    args = parser.parse_args()
    errors, links = validate_page()
    if args.check_external:
        errors.extend(check_external_links(links))
    if errors:
        print("SITE_CHECK_FAILED", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print("SITE_CHECK_OK")
    if args.check_external:
        print("SITE_EXTERNAL_LINKS_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
