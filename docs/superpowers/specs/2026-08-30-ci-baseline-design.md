# Repository CI Baseline Design

## Goal

Replace feature-specific GitHub Actions verification workflows with one repository-wide CI baseline that automatically validates every pull request into `master` and every push to `master`.

## Scope

The CI baseline must:

- run Android JVM unit tests with `testDebugUnitTest`;
- run Android lint with `lintDebug`;
- build the debug APK with `assembleDebug`;
- use JDK 21;
- cache Gradle state to reduce repeated setup time;
- cancel obsolete runs when a newer commit arrives for the same PR or branch;
- use one job so Gradle state is reused within the run;
- preserve the repository's existing Android lint debt in a checked-in lint baseline so new lint errors fail CI without requiring unrelated cleanup first;
- upload unit-test and lint reports for failed runs so failures can be diagnosed without reproducing them locally;
- avoid emulator/instrumentation tests for now because they add substantial runtime and maintenance cost;
- replace the existing temporary `.github/workflows/men-with-the-pot-verification.yml` workflow.

## Workflow structure

Create `.github/workflows/ci.yml` with `pull_request` and `push` triggers scoped to `master`.

The single `verify` job runs on `ubuntu-latest`, uses `actions/checkout@v6`, installs Temurin JDK 21 with `actions/setup-java@v5`, enables Gradle state caching with `gradle/actions/setup-gradle@v5`, and runs the three verification tasks in one Gradle invocation:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace
```

Using one invocation minimizes Gradle configuration overhead while still failing the workflow when any verification task fails.

If verification fails, `actions/upload-artifact@v7` stores the generated unit-test and lint reports for 7 days. Successful runs do not upload diagnostic artifacts.

## Android lint baseline

The first repository-wide lint run exposed pre-existing lint debt that the old workflow never checked. Unit tests and `assembleDebug` passed; `lintDebug` failed on existing findings.

Configure `app/build.gradle.kts` to use `app/lint-baseline.xml`. The generated baseline records existing findings only. This keeps the new CI useful immediately: existing debt does not block unrelated development, while newly introduced lint findings still fail `lintDebug`.

The lint baseline can be reduced over time as existing findings are fixed; it should not be regenerated casually because doing so would hide newly introduced problems.

## Concurrency

Use a concurrency group derived from the workflow plus PR number or Git ref, with `cancel-in-progress: true`. A new push therefore cancels stale CI for the same PR/branch instead of spending runner time on obsolete code.

## Bootstrap verification

Because GitHub does not use a newly added `pull_request` workflow from the feature branch until that workflow exists on the base branch, temporarily allow pushes to the CI implementation branch during setup. Remove that temporary branch trigger before merge. The production workflow remains scoped only to PRs targeting `master` and pushes to `master`.

## Out of scope

- Android instrumentation/emulator tests.
- Release APK/AAB builds or signing.
- Code coverage thresholds.
- Additional static-analysis dependencies.
- Branch protection configuration.
