# Repository CI Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace temporary branch-specific verification with one fast CI workflow for every PR to `master` and every push to `master`.

**Architecture:** Use one GitHub Actions `verify` job so checkout/JDK/Gradle setup happens once and Gradle can reuse state across unit tests, lint, and debug build. Use GitHub concurrency cancellation and the official Gradle setup action to reduce wasted runner time and repeated dependency setup. Preserve existing lint findings in a checked-in Android lint baseline so CI blocks regressions rather than unrelated historic debt.

**Tech Stack:** GitHub Actions, Gradle, Android Gradle Plugin, Temurin JDK 21

**Spec:** `docs/superpowers/specs/2026-08-30-ci-baseline-design.md`

## Global Constraints

- CI runs on pull requests targeting `master` and pushes to `master`.
- Verification consists of `testDebugUnitTest`, `lintDebug`, and `assembleDebug`.
- JDK version is 21.
- Emulator/instrumentation tests remain out of scope.
- Existing temporary `men-with-the-pot-verification.yml` is removed.
- Existing lint findings are captured in `app/lint-baseline.xml`; new findings must still fail CI.

---

### Task 1: Replace temporary verification with repository-wide CI

**Files:**
- Create: `.github/workflows/ci.yml`
- Delete: `.github/workflows/men-with-the-pot-verification.yml`
- Modify: `app/build.gradle.kts`
- Create: `app/lint-baseline.xml`

**Interfaces:**
- Consumes: existing Gradle wrapper and Android `app` tasks.
- Produces: GitHub Actions check named `CI / verify` for PRs and master pushes.

- [x] **Step 1: Create the general CI workflow**

The final workflow uses:

```yaml
name: CI

on:
  pull_request:
    branches:
      - master
  push:
    branches:
      - master

permissions:
  contents: read

concurrency:
  group: ci-${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: true

jobs:
  verify:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v5
      - name: Verify
        run: ./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace
      - name: Upload verification reports
        if: failure()
        uses: actions/upload-artifact@v7
        with:
          name: verification-reports-${{ github.run_id }}
          path: |
            app/build/reports/tests/
            app/build/reports/lint-results-debug.*
            app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt
          if-no-files-found: ignore
          retention-days: 7
```

- [x] **Step 2: Remove the temporary verification workflow**

Delete `.github/workflows/men-with-the-pot-verification.yml`; the new workflow covers its unit-test and debug-build checks for all future PRs instead of selected branches.

- [x] **Step 3: Bootstrap and verify the workflow**

Temporarily allow pushes to `chore/ci-baseline` because the new PR-triggered workflow does not yet exist on the base branch. Run the real workflow remotely, then remove this temporary trigger before merge.

- [x] **Step 4: Capture existing lint debt**

The first full verification confirmed `testDebugUnitTest` and `assembleDebug` pass, while `lintDebug` fails on pre-existing findings. Configure:

```kotlin
lint {
    baseline = file("lint-baseline.xml")
}
```

Generate and check in `app/lint-baseline.xml`. Do not disable lint and do not regenerate the baseline as part of normal CI runs.

- [x] **Step 5: Verify tests, lint, and debug build together**

Run remotely:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace
```

Expected and observed: all three tasks complete successfully with the checked-in lint baseline.

- [x] **Step 6: Finalize production triggers**

Remove the temporary `chore/ci-baseline` push trigger. The final workflow is scoped only to PRs targeting `master` and pushes to `master`.

- [ ] **Step 7: Merge and verify `master`**

Merge PR #25 into `master`. Confirm the merge push triggers `CI / verify` and completes successfully; this also seeds writable Gradle cache state on the default branch for future CI runs.
