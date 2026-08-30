# Repository CI Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace temporary branch-specific verification with one fast CI workflow for every PR to `master` and every push to `master`.

**Architecture:** Use one GitHub Actions `verify` job so checkout/JDK/Gradle setup happens once and Gradle can reuse state across unit tests, lint, and debug build. Use GitHub concurrency cancellation and the official Gradle setup action to reduce wasted runner time and repeated dependency setup.

**Tech Stack:** GitHub Actions, Gradle, Android Gradle Plugin, Temurin JDK 21

**Spec:** `docs/superpowers/specs/2026-08-30-ci-baseline-design.md`

## Global Constraints

- CI runs on pull requests targeting `master` and pushes to `master`.
- Verification consists of `testDebugUnitTest`, `lintDebug`, and `assembleDebug`.
- JDK version is 21.
- Emulator/instrumentation tests remain out of scope.
- Existing temporary `men-with-the-pot-verification.yml` is removed.

---

### Task 1: Replace temporary verification with repository-wide CI

**Files:**
- Create: `.github/workflows/ci.yml`
- Delete: `.github/workflows/men-with-the-pot-verification.yml`

**Interfaces:**
- Consumes: existing Gradle wrapper and Android `app` tasks.
- Produces: GitHub Actions check named `CI / verify` for PRs and master pushes.

- [ ] **Step 1: Create the general CI workflow**

Create `.github/workflows/ci.yml` with this content:

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
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v4
      - name: Verify
        run: ./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace
```

- [ ] **Step 2: Remove the temporary verification workflow**

Delete `.github/workflows/men-with-the-pot-verification.yml`; the new workflow covers its unit-test and debug-build checks for all future PRs instead of selected branches.

- [ ] **Step 3: Open a pull request into `master`**

The PR itself must trigger the newly added `CI` workflow.

- [ ] **Step 4: Verify the remote CI run**

Confirm the `verify` job executes all three Gradle tasks and finishes successfully. If it fails, inspect the failed GitHub Actions step and adjust only what is necessary to make the approved baseline pass.

- [ ] **Step 5: Merge after successful verification**

Merge the PR into `master`; the merge push should then trigger the same CI workflow on `master`.
