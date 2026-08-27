---
name: code-review
description: Review pull requests in this repository for correctness bugs, style issues, and missed edge cases before they are merged.
---

# Code Review

Use this skill when reviewing a pull request in this repository.

## What to check

- **Correctness**: does the change do what the PR description claims, and are there
  edge cases (nulls, empty lists, configuration changes) that aren't handled?
- **Kotlin/Android conventions**: idiomatic Kotlin, correct use of Compose state and
  lifecycle APIs, no blocking calls on the main thread.
- **Build health**: Gradle files stay valid — no unresolved plugins/dependencies, and
  `./gradlew assembleDebug` should succeed.
- **Tests**: new behavior has test coverage where practical, and no existing tests are
  weakened or disabled to make the PR pass.
- **Scope**: the diff matches what the PR claims to do, without unrelated changes.

## How to respond

- Leave specific, actionable comments referencing the file and line.
- Flag anything that would break the CI build or introduce a regression as blocking.
- Note minor style nits separately from correctness issues so authors can triage.
