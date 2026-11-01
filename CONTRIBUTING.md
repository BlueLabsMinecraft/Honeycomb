# Contributing to Honeycomb

Thanks for taking the time. This document describes how the project is built, what
is expected from a change, and how to test it.

## Before you start

* Open an issue for anything larger than a bug fix or a small improvement, so the
  design can be agreed before code exists.
* One change per pull request. Mixing a refactor with a behaviour change makes the
  review slower and the revert riskier.

## Building

```bash
./gradlew build                     # everything, including tests
./gradlew :honeycomb-legacy:build    # Java 21 / Minecraft 1.21.x artifact
./gradlew :honeycomb-modern:build    # Java 25 / Minecraft 26.x artifact
```

You need JDK 21 and JDK 25 available. Gradle picks them up through toolchains; if
it cannot, set `org.gradle.java.installations.paths` in `gradle.properties`.

Both artifacts are compiled from the same sources in `shared/`. Anything you add
must compile against both Paper APIs. If an API only exists in one of them, put the
version specific part behind a small adapter class and load it reflectively, the
way the optional integrations are loaded.

## Code style

* Java 21 language level for the legacy artifact: no `switch` patterns that need
  22+, no unnamed variables.
* Four spaces, no tabs. Keep lines around 100 columns.
* Name things after what they hold, not what they are: `NametagService`, not
  `NametagManagerImpl`.
* Small methods and one responsibility per class. A class that needs a table of
  contents is a class that should be split.
* Comments explain *why*. The code already says what.
* No `TODO` in place of behaviour. If something is not implemented, it does not
  ship, and the README does not claim it does.

## Testing

* Unit tests live in `shared/src/test/java` and must not touch Bukkit statics:
  the logic under test should be extractable into a plain class. Look at
  `PlaceholderService`, `Animation` and `SortingRule` for the pattern.
* Display code is tested by running a server with a headless client; see
  `tools/smoke-test/README.md`.
* A pull request that changes rendering should say which of these you ran.

## What will be rejected

* Hardcoded display values, fake placeholders, or a command that reports success
  without doing anything.
* New mandatory dependencies. Everything except Paper stays optional.
* Reflection where an official API exists.
* Unbounded repeating tasks: every scheduled task must be registered with
  `TaskRegistry` so it can be cancelled on reload and disable.

## Commit messages

`area: short summary` in the imperative mood, for example
`nametag: only resend prefixes that changed`. The body explains the reason, not the
diff.
