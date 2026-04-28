---
date: 2026-04-28
topic: remove-leanback
status: active
origin: docs/brainstorms/2026-04-28-remove-leanback-requirements.md
---

# Remove Leanback Implementation Plan

> **For agentic workers:** Implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for progress tracking.

## Goal

Remove Android Leanback from the app completely while preserving the new custom TV/phone playback, playlist, EPG, and settings experience.

The migration happens in two stages:

1. Move channel/playback ownership out of the Leanback `MainFragment`.
2. Delete Leanback UI files, styles, imports, and Gradle dependency.

## Origin

Source requirements: `docs/brainstorms/2026-04-28-remove-leanback-requirements.md`

## Requirements Trace

- R1-R3: Tasks 1, 5, 6
- R4-R8: Tasks 1, 2, 3
- R9-R12: Tasks 1, 3, 4
- R13-R16: Tasks 3, 4, 5
- R17-R19: Tasks 5, 6

## Current State Summary

Leanback usage is currently limited to:

- `app/src/main/java/com/gamaxtersnow/mytv/MainFragment.kt`
- `app/src/main/java/com/gamaxtersnow/mytv/CardPresenter.kt`
- `app/src/main/java/com/gamaxtersnow/mytv/ErrorFragment.kt`
- `app/src/main/res/values/styles.xml`
- `app/build.gradle`

`MainFragment` still owns important behavior that must be preserved before deletion:

- Builds `TVListViewModel` from `TVList.list`.
- Applies cached XMLTV EPG from `MyApplication.epgRepository`.
- Triggers FEPG requests for channels with `ProgramType.F`.
- Maintains current channel position and persists it through `SP.itemPosition`.
- Implements `play`, `prev`, and `next`.
- Suppresses empty and duplicated playback URLs.
- Calls `MainActivity.play(...)` and `MainActivity.showInfoFragment(...)`.

## Key Decisions

- Add `ChannelPlaybackCoordinator` as a lightweight, non-UI owner for channel list state and playback commands.
- Keep `TVListViewModel` and `TVViewModel` for now to minimize churn in playlist, EPG, and player code.
- Let `MainActivity` own the coordinator lifecycle and provide callbacks for playback, info/number overlays, and errors.
- Keep duplicated URL suppression in the coordinator for compatibility with the old `MainFragment.check(...)` behavior.
- Replace `ErrorFragment` in place with a normal custom `Fragment`, keeping the class name to reduce call-site churn.
- Keep `android.software.leanback` manifest feature/category support unless manual/device verification shows it should change. This is Android TV distribution metadata, not the Leanback UI dependency.

## Scope Boundaries

In scope:

- Remove Leanback from production source and Gradle dependencies.
- Preserve playback and overlay behavior.
- Add unit tests for channel/playback coordination.
- Update `MainActivity`, settings refresh, playlist, and EPG entry points to use the coordinator.
- Replace Leanback error UI with custom XML/Fragment.

Out of scope:

- Redesigning playback, playlist, EPG, or settings visuals.
- Introducing Compose, Navigation, or a new architecture framework.
- Rewriting player engine or stream source selection logic beyond moving ownership.
- Reworking EPG repository/cache internals.

## File Map

Create:

- `app/src/main/java/com/gamaxtersnow/mytv/ChannelPlaybackCoordinator.kt`
- `app/src/main/res/layout/error.xml`
- `app/src/test/java/com/gamaxtersnow/mytv/ChannelPlaybackCoordinatorTest.kt`

Modify:

- `app/src/main/java/com/gamaxtersnow/mytv/MainActivity.kt`
- `app/src/main/java/com/gamaxtersnow/mytv/SettingFragment.kt`
- `app/src/main/java/com/gamaxtersnow/mytv/ErrorFragment.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/styles.xml`
- `app/build.gradle`
- Existing focused tests as needed.

Delete:

- `app/src/main/java/com/gamaxtersnow/mytv/MainFragment.kt`
- `app/src/main/java/com/gamaxtersnow/mytv/CardPresenter.kt`

## Implementation Units

### Task 0: Baseline

**Files:** no source changes.

- [ ] Confirm the branch is `feat/tv-phone-iptv-ui-redesign` or a follow-up feature branch.
- [ ] Run baseline unit tests.

Verification:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest
```

Expected: baseline tests pass before refactor changes.

### Task 1: Add ChannelPlaybackCoordinator

**Requirements:** R1-R12

**Files:**

- Create: `app/src/main/java/com/gamaxtersnow/mytv/ChannelPlaybackCoordinator.kt`
- Create: `app/src/test/java/com/gamaxtersnow/mytv/ChannelPlaybackCoordinatorTest.kt`
- Modify if needed: `app/src/main/java/com/gamaxtersnow/mytv/models/TVListViewModel.kt`

**Execution note:** Characterization-first. Capture current `MainFragment` channel/playback behavior in coordinator tests before removing the old fragment.

- [ ] Add `ChannelPlaybackCoordinator` as a non-UI class.
- [ ] Keep it small and explicit. It should own:
  - a `TVListViewModel`
  - current channel position
  - last played URL
  - channel list rebuild from `TVList.list`
  - cached EPG application through an injected repository/provider
  - playback commands: `startFromPersistedPosition`, `play(id)`, `prev()`, `next()`, `reloadChannels()`
- [ ] Use callbacks/listeners rather than direct fragment references:
  - playback requested with `TVViewModel`
  - info/number overlay requested with `TVViewModel`
  - user-facing error message requested
  - FEPG fetch requested for a `TVViewModel`
  - authenticated stream fetch requested for a `TVViewModel`
- [ ] Preserve old invalid/empty/duplicate URL handling from `MainFragment.check(...)`.
- [ ] Persist current channel position through an injected getter/setter or `SP` wrapper so tests can avoid Android framework dependency.
- [ ] Add tests for:
  - persisted valid position starts that channel
  - invalid persisted position falls back to channel 0
  - `play(id)` updates current position and requests playback
  - invalid `play(id)` emits the existing "频道不存在" behavior
  - `prev()` wraps from first to last
  - `next()` wraps from last to first
  - empty URL does not request playback and emits an error
  - duplicate current URL does not request playback twice
  - cached EPG programmes are attached during channel rebuild
  - FEPG request callback is invoked for channels that need it

Patterns to follow:

- `app/src/main/java/com/gamaxtersnow/mytv/MainFragment.kt` for legacy behavior to preserve.
- `app/src/main/java/com/gamaxtersnow/mytv/models/TVListViewModel.kt` for existing channel state model.
- `app/src/test/java/com/gamaxtersnow/mytv/models/ChannelPanelStateTest.kt` for LiveData unit-test style.

Verification:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest --tests 'com.gamaxtersnow.mytv.ChannelPlaybackCoordinatorTest'
```

Expected: coordinator tests pass.

### Task 2: Move MainActivity to Coordinator

**Requirements:** R2, R4-R15

**Files:**

- Modify: `app/src/main/java/com/gamaxtersnow/mytv/MainActivity.kt`
- Modify: `app/src/main/java/com/gamaxtersnow/mytv/SettingFragment.kt`

**Execution note:** Characterization-first where possible. Keep `MainFragment` in the repo but remove it from the active runtime path.

- [ ] Instantiate `ChannelPlaybackCoordinator` in `MainActivity`.
- [ ] Replace `mainFragment.tvListViewModel` reads with `coordinator.tvListViewModel`.
- [ ] Replace `mainFragment.play(...)`, `prev()`, and `next()` routing with coordinator calls.
- [ ] Replace `mainFragment.reloadRows()` with coordinator reload.
- [ ] Reconnect coordinator callbacks:
  - `onPlaybackRequested` -> `playerFragment.play(tvViewModel)`
  - `onInfoRequested` -> `showInfoFragment(tvViewModel)`
  - `onErrorRequested` -> current error surface
  - `onFetchDataRequested` -> `Request.fetchData(tvViewModel)`
  - `onFetchFepgRequested` -> `Request.fetchFEPG(tvViewModel)`
- [ ] Preserve current remote playlist refresh flow:
  - `TVList.refresh(context)`
  - coordinator reload
  - playlist/EPG panels refresh against coordinator state
- [ ] Save current channel position on `onStop` or equivalent coordinator lifecycle call.
- [ ] Remove runtime dependency on Leanback visibility checks:
  - replace `mainFragment.isHidden` checks with explicit overlay state checks or "no panel open" helpers
  - remove `hideLegacyMainFragment()` and `legacyMainFragmentIsHidden()` from active behavior

Patterns to follow:

- Current `MainActivity` overlay routing methods.
- `ChannelPanelFragment.show(tvListViewModel)` and `EpgPanelFragment.show(tvListViewModel)`.

Verification:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest
```

Expected: tests pass after `MainActivity` no longer uses `MainFragment` as the active state owner.

### Task 3: Stop Creating Leanback MainFragment

**Requirements:** R13-R15

**Files:**

- Modify: `app/src/main/java/com/gamaxtersnow/mytv/MainActivity.kt`
- Modify: `app/src/main/res/layout/activity_main.xml`

- [ ] Remove `MainFragment` from `MainActivity` fields and fragment transactions.
- [ ] Adjust readiness/startup flow so initial playback starts from coordinator after:
  - player fragment is ready
  - request/network readiness behavior remains equivalent
  - TV list has been loaded/refreshed
- [ ] Rename `main_browse_fragment` container if helpful, or keep it as a generic root container if renaming creates unnecessary churn.
- [ ] Ensure single tap, double tap, DPAD, back, channel up/down, and menu/settings routes still work without Leanback fragment visibility.
- [ ] Ensure playlist and EPG panels still open with current channel selected.

Verification:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug
```

Expected: app builds without instantiating `MainFragment`.

### Task 4: Replace Leanback ErrorFragment

**Requirements:** R15-R16

**Files:**

- Modify: `app/src/main/java/com/gamaxtersnow/mytv/ErrorFragment.kt`
- Create: `app/src/main/res/layout/error.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] Convert `ErrorFragment` from `ErrorSupportFragment` to a regular `Fragment`.
- [ ] Add a small custom error layout with:
  - message text
  - dismiss button
  - dark translucent background compatible with playback overlay style
- [ ] Keep `setErrorContent(message)` or an equivalent API so `MainActivity` call sites stay simple.
- [ ] Preserve dismiss behavior.
- [ ] Avoid Leanback drawable/resource references.

Patterns to follow:

- Existing custom dark overlay resources in `app/src/main/res/drawable/iptv_overlay_panel_bg.xml`.
- Existing `dismiss_error` string.

Verification:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug
```

Expected: error UI builds without Leanback imports.

### Task 5: Delete Leanback UI and Dependency

**Requirements:** R3, R17-R19

**Files:**

- Delete: `app/src/main/java/com/gamaxtersnow/mytv/MainFragment.kt`
- Delete: `app/src/main/java/com/gamaxtersnow/mytv/CardPresenter.kt`
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `app/build.gradle`

- [ ] Delete `MainFragment.kt`.
- [ ] Delete `CardPresenter.kt`.
- [ ] Remove Leanback card styles from `styles.xml`; delete the file if it becomes empty and no resource references remain.
- [ ] Remove `implementation 'androidx.leanback:leanback:1.2.0-alpha04'` from `app/build.gradle`.
- [ ] Search and remove remaining production source imports/usages:

```bash
rg "androidx\\.leanback|BrowseSupportFragment|ImageCardView|ErrorSupportFragment|ArrayObjectAdapter|ListRow|Presenter|RowPresenter|HeaderItem|SelectItemViewHolderTask" app/src/main app/build.gradle
```

Expected: no production source usages remain. Manifest TV feature/category entries may remain because they are Android TV metadata, not Leanback library API.

Verification:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug
```

Expected: tests and debug build pass without the Leanback dependency.

### Task 6: Manual Regression Pass

**Requirements:** R1-R19

**Files:** no new production files expected.

- [ ] TV/remote checks:
  - app starts playback
  - up/down changes channel
  - center/left opens playlist
  - right/menu opens settings
  - help/guide opens EPG
  - back closes active overlay before exit
- [ ] Phone checks:
  - portrait single tap opens playback overlay
  - playlist, EPG, and settings open from overlay actions
  - landscape keeps video dominant
  - rotation does not restart playback
- [ ] Data checks:
  - remote playlist refresh rebuilds channel state
  - EPG cache appears in playlist/EPG rows
  - manual EPG refresh failure preserves prior data
  - persisted channel position is restored after app restart

Expected: no user-visible regression from removing Leanback.

## Suggested Commit Sequence

1. `feat: add channel playback coordinator`
2. `refactor: route playback state outside leanback`
3. `refactor: stop creating leanback browser`
4. `refactor: replace leanback error screen`
5. `chore: remove leanback dependency`

## Risks

- Startup ordering may regress because `MainFragment.fragmentReady()` currently triggers first playback. Mitigation: coordinator startup should be explicit and covered by tests where possible.
- `TVViewModel.changed()` observers are currently Fragment-lifecycle-bound. Mitigation: move the triggering logic into coordinator callbacks and avoid relying on hidden UI lifecycle.
- Duplicate URL suppression may prevent legitimate replay. Mitigation: preserve existing behavior first; revisit only after Leanback removal is complete.
- Removing Leanback may expose missing focus states in custom TV views. Mitigation: manual TV remote verification and focusable XML checks.
- Error display could regress if it previously relied on `ErrorSupportFragment` background/button behavior. Mitigation: build a minimal custom overlay and verify dismiss flow.

## Open Implementation Notes

- Keep the first implementation conservative. The goal is removal and simplification, not a broader app architecture rewrite.
- Prefer adding coordinator tests before cutting `MainFragment` out of the runtime path.
- If a behavior is hard to unit test because it crosses Android framework/lifecycle boundaries, isolate the pure decision in the coordinator and leave fragment wiring for build/manual verification.
