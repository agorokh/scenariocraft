# Design review round 1: make the Speed Build page feel like one game

This prompt is itself part of the project story. Before you change anything, commit this
file verbatim to `docs/design-council/2026-07-20-review-round-1.md` so the review is
preserved the same way our other "Decisions by the design council" are.

## Where this review came from

We sat down with the live page (https://agorokh.github.io/scenariocraft/) as a family
design council, the same council from the footer. Our art director (10 years old, author
of the name and the logo) read the whole page. Her verdict: the logo credit and the
authorship story made her day. Those lines are now officially the most protected pixels
on the site. The council's verdict on everything else: the page reads like three
different pages wearing one logo. Steps 1-3 are one illustration style, steps 4-6 are
framed screenshots in a second style, step 7 is a third style of CSS cards, and the
typography never echoes the chunky block letters of the logo she drew.

Your job: make it feel like ONE game, designed by the same hands that made that logo.
Scope: `site/` only (`index.html`, `styles.css`, assets). No build tooling changes.

## Untouchables (do not edit, move, shrink, or reword)

1. The logo (`site/assets/branding/speed-build-logo.png`) and its hero placement.
2. The footer credits, verbatim: "name & logo by our 10-year-old designer, working with
   ChatGPT" and "Built by a family design council and Codex for OpenAI Build Week".
3. The disclaimer: "NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED
   WITH MOJANG OR MICROSOFT."
4. Every honesty label: "Sample commentary", "Builder names are invented for this public
   tour", and the whole "Every image has a trail home" provenance section. You may
   restyle them; you may never remove, hide, or soften them.
5. Names and privacy: invented builder names only (Blocky and friends). Never add a real
   name, a photo of a person, an age beyond what the footer already says, or any
   location detail.
6. Accessibility scaffolding: skip link, aria labels, alt text, figure semantics. Keep
   or improve, never drop.
7. No external CDNs, trackers, analytics, or hotlinked fonts. Anything new gets
   self-hosted inside `site/`.

## What to change

### 1. Typography: let the logo pick the font
Add ONE display typeface that rhymes with the blocky logo mark (a pixel or chunky
rounded face, self-hosted woff2, OFL-licensed, with a system-ui fallback stack). Use it
for h1, h2, step numbers, and the reveal card only. Body copy stays a readable system
sans. Then collapse the roughly 25 distinct font-size values (a mix of rem, px, em, and
clamp) into a 5-step scale defined once as CSS variables (--fs-sm, --fs-base, --fs-md,
--fs-lg, --fs-display). Every rule references the scale; zero one-off sizes survive.

### 2. Color: one core palette, accents with jobs
Core palette derived from the logo: the grass greens, the deepslate darks, plus gold
for wins. The rainbow accents (pink, blue, purple, violet, orange, yellow) currently
decorate at random; give each one a job or delete it. Rule: bright accents belong only
to characters, one hue per judge persona (Brickworth, Sparkle, Redstone) and one hue per
builder figure in the SVG scenes, used consistently everywhere that character appears.
Remove every one-off hex that is not a CSS variable.

### 3. One frame to rule all figures
The three figure treatments (inline SVG scenes, framed voxel renders, CSS judge cards)
must become siblings: identical card container, identical border weight, identical
corner treatment, identical hard-offset block shadow. Pick one shadow system (single
dark offset, two sizes: small for chips and badges, large for cards) and delete the
doubled two-layer shadow. The bossbar, the reveal card, and the scoreboard should look
like parts of the same in-game UI kit.

### 4. Rhythm and flow
Keep the 7-step alternating left/right layout; it works. Normalize vertical spacing to
one spacing scale so the page scrolls like a comic strip, not a scrapbook. The step
number, kicker, and heading block should have identical geometry in every step.

### 5. Kid-legibility pass
Our council includes a 7-year-old. Every sentence should survive being read aloud to
her. Where a sentence needs jargon (bossbar, voxel), keep the playful in-page
explanation pattern the page already uses.

### 6. Mobile
The logo image is 1535x1024 and currently dominates small screens. At 375px wide the
hero, the step cards, the judge grid, and the scoreboard must all be comfortable, with
no horizontal scroll.

## Acceptance checklist (include results in your PR description)

- Before/after counts: distinct font-size values, distinct color values outside
  variables, distinct shadow declarations. All three must drop sharply.
- Screenshots at 1280px and 375px, light and dark backgrounds if supported.
- Lighthouse accessibility score 95 or higher; contrast AA on all text.
- Diff shows zero changes to the untouchable items above.
- The page still says exactly what it said: no copy rewrites beyond what section 5
  strictly requires, and none inside untouchables.

One more thing from the council: do not make it tasteful-boring. The energy, the speed
lines, the confetti, the personalities stay. We are tuning the orchestra, not firing it.
