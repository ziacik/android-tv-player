# Deploy Command Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide `./deploy` for build, install, and launch on the Android TV.

**Architecture:** A POSIX shell script uses `set -eu`, defaults to the Philips ADB serial, and accepts one optional serial argument.

**Tech Stack:** POSIX shell, Gradle wrapper, ADB.

---

### Task 1: Add and verify the command

**Files:**
- Create: `deploy`

- [ ] Write the command with `set -eu`, `target="${1:-192.168.0.200:5555}"`, `./gradlew assembleDebug`, `adb -s "$target" install -r app/build/outputs/apk/debug/app-debug.apk`, force-stop, and `monkey` launch.
- [ ] Mark it executable and run `sh -n deploy`.
- [ ] Run `./deploy` and confirm Gradle build, ADB install, and app launch succeed on the default target.
