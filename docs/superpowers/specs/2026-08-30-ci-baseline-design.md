# Repository CI Baseline Design

## Goal

Replace feature-specific GitHub Actions verification workflows with one repository-wide CI baseline that automatically validates every pull request into `master` and every push to `master`.

## Scope

The CI baseline must:

- run Android JVM unit tests with `testDebugUnitTest`;
- run Android lint with `lintDebug`;
- build the debug APK with `assembleDebug`;
- use JDK 21, matching the existing successful verification workflow;
- cache Gradle state to reduce repeated setup time;
- cancel obsolete runs when a newer commit arrives for the same PR or branch;
- use one job so Gradle state is reused within the run;
- avoid emulator/instrumentation tests for now because they add substantial runtime and maintenance cost;
- replace the existing temporary `.github/workflows/men-with-the-pot-verification.yml` workflow.

## Workflow structure

Create `.github/workflows/ci.yml` with `pull_request` and `push` triggers scoped to `master`.

The single `verify` job runs on `ubuntu-latest`, checks out the repository, installs Temurin JDK 21, enables the official Gradle Actions cache, and runs the three verification tasks in one Gradle invocation:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace
```

Using one invocation minimizes Gradle configuration overhead while still failing the workflow immediately when any verification task fails.

## Concurrency

Use a concurrency group derived from the workflow plus PR number or Git ref, with `cancel-in-progress: true`. A new push therefore cancels stale CI for the same PR/branch instead of spending runner time on obsolete code.

## Out of scope

- Android instrumentation/emulator tests.
- Release APK/AAB builds or signing.
- Code coverage thresholds.
- Additional static-analysis dependencies.
- Branch protection configuration.
