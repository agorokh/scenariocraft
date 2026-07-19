# ScenarioCraft

ScenarioCraft turns our kids' game ideas into playable family rituals: they invent a scenario,
we shape it into a spec together, an AI coding agent builds it, and an AI panel referees the
match while we play. Build Battle is reference scenario #1, not the product. The product is
the repeatable, kid-legible, parent-guidable, agent-buildable, warmly judged loop that can
welcome whatever scenario comes up in the car next week.

> **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR
> MICROSOFT.**

## The co-creators

- A 7-year-old game designer and judge auditor.
- A 10-year-old game designer and judge auditor.
- A 17-year-old contributing engineer working in his own Codex sessions.
- Their parent, facilitating the build and playing every round.

Their design feedback is product input. Relevant choices are recorded as “Decisions by the
design council” without publishing any child's name.

## First scenario: Build Battle

Contestants receive a secret build prompt, create in private plots, tour the finished builds,
and hear a warm three-persona AI panel score every entry against one shared rubric.

Gameplay arrives through the Build Week issue sequence. This scaffold intentionally contains
no game logic yet.

## Quickstart

Quickstart instructions will be added with the runnable demo instance in BB-12. Contributors
can build the scaffold now with Java 21:

```sh
./gradlew build
```

The plugin jar is written to `build/libs/`.

## How this was built

ScenarioCraft is being built in the open for **OpenAI Build Week (July 2026)**. Every change
starts from a GitHub issue and is implemented by Codex.

The engineering discipline used here was not invented for Build Week. It is a public port of
a privately evolved, multi-model agentic engineering system: pitfall ledgers, decision logs,
eval gates, and session handoffs. ScenarioCraft is the first public project built with that
method and Codex as its sole software builder.

## License

ScenarioCraft is available under the [MIT License](LICENSE).
