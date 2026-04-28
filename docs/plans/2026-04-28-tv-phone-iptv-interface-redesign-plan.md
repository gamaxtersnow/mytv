# TV and Phone IPTV Interface Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the TV and phone IPTV interface redesign described in `docs/brainstorms/2026-04-28-tv-phone-iptv-interface-redesign-requirements.md`.

**Architecture:** Add a small UI mode layer that classifies the current surface as TV, phone portrait, or phone landscape, then let existing fragments configure themselves from that mode. Add an EPG cache/repository layer that persists XMLTV data, refreshes it in the background, builds an in-memory channel index, and exposes shared programme summaries so playback, playlist, EPG, and settings use the same current/next programme data.

**Tech Stack:** Android XML layouts, Kotlin, FragmentActivity/DialogFragment/Fragment, ViewBinding, RecyclerView, Media3 PlayerView, JUnit4.

---

## Origin

Source requirements: `docs/brainstorms/2026-04-28-tv-phone-iptv-interface-redesign-requirements.md`

Pencil references:

- TV EPG: `bi8Au`
- Phone EPG: `M5m6U`
- TV settings: `G3ddD`
- Phone settings: `dN8Y9`
- TV playlist: `w9xLg`
- Phone playlist: `nBgFd`
- TV playback: `LdkiC`
- Phone playback: `F1Zimz`

## Scope

In scope:

- TV and phone layouts for playback, playlist, EPG, and settings.
- Phone portrait and phone landscape behavior.
- EPG XMLTV persistent cache, refresh, parsing, and in-memory programme index.
- Shared current/next programme summary data.
- Moving existing player debug/performance UI out of the normal playback overlay.
- Build and focused unit tests.

Out of scope:

- Replacing the selected EPG provider/source.
- A full timeline-grid EPG.
- Video decoder/player engine rewrites.
- Pixel-perfect reproduction of Pencil mockups.

## Key Decisions

- Use runtime UI mode detection instead of relying only on resource qualifiers. Phone landscape and TV landscape need different behavior, so width/orientation alone is not enough.
- Preserve `MainActivity` and current fragment ownership. The redesign is broad enough without replacing navigation architecture.
- Keep `ChannelPanelFragment` as the playlist implementation and add a new `EpgPanelFragment` for richer programme browsing.
- Treat existing `player_status_overlay.xml`, `player_control_panel.xml`, and `performance_monitor_panel.xml` as advanced/debug UI. Normal playback should use a separate lightweight overlay.
- Remove forced landscape from `MainActivity` and handle configuration changes in place so rotation does not restart playback.
- Implement EPG cache before UI binding. UI surfaces should consume cached/indexed programme summaries rather than triggering per-screen EPG requests.

## File Map

Create:

- `app/src/main/java/com/gamaxtersnow/mytv/ui/AppUiMode.kt` - classifies TV, phone portrait, and phone landscape.
- `app/src/main/java/com/gamaxtersnow/mytv/epg/EpgCacheManager.kt` - persists and loads XMLTV cache files.
- `app/src/main/java/com/gamaxtersnow/mytv/epg/XmlTvParser.kt` - parses XMLTV into programme records.
- `app/src/main/java/com/gamaxtersnow/mytv/epg/EpgRepository.kt` - coordinates cache-first load, background refresh, and in-memory index.
- `app/src/main/java/com/gamaxtersnow/mytv/epg/EpgStatus.kt` - exposes source, last update, date range, and availability.
- `app/src/main/java/com/gamaxtersnow/mytv/models/ProgramSummary.kt` - current/next programme data and progress.
- `app/src/main/java/com/gamaxtersnow/mytv/EpgPanelFragment.kt` - dedicated EPG overlay/page.
- `app/src/main/res/layout/player_daily_overlay.xml` - normal playback overlay.
- `app/src/main/res/layout/epg_panel.xml` - EPG panel container.
- `app/src/main/res/layout/epg_panel_row.xml` - EPG channel row.
- `app/src/test/java/com/gamaxtersnow/mytv/ui/AppUiModeTest.kt`
- `app/src/test/java/com/gamaxtersnow/mytv/epg/XmlTvParserTest.kt`
- `app/src/test/java/com/gamaxtersnow/mytv/epg/EpgCacheManagerTest.kt`
- `app/src/test/java/com/gamaxtersnow/mytv/epg/EpgRepositoryTest.kt`
- `app/src/test/java/com/gamaxtersnow/mytv/models/ProgramSummaryTest.kt`
- `app/src/test/java/com/gamaxtersnow/mytv/models/EpgPanelStateTest.kt`

Modify:

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/gamaxtersnow/mytv/MainActivity.kt`
- `app/src/main/java/com/gamaxtersnow/mytv/Request.kt`
- `app/src/main/java/com/gamaxtersnow/mytv/SP.kt`
- `app/src/main/java/com/gamaxtersnow/mytv/PlayerFragment.kt`
- `app/src/main/java/com/gamaxtersnow/mytv/PlayerUiController.kt`
- `app/src/main/java/com/gamaxtersnow/mytv/InfoFragment.kt`
- `app/src/main/java/com/gamaxtersnow/mytv/ChannelPanelFragment.kt`
- `app/src/main/java/com/gamaxtersnow/mytv/SettingFragment.kt`
- `app/src/main/java/com/gamaxtersnow/mytv/models/TVListViewModel.kt`
- `app/src/main/res/layout/player.xml`
- `app/src/main/res/layout/info.xml`
- `app/src/main/res/layout/channel_panel.xml`
- `app/src/main/res/layout/channel_panel_row.xml`
- `app/src/main/res/layout/setting.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/gamaxtersnow/mytv/models/ChannelPanelStateTest.kt`

## Task 0: Branch And Baseline

**Files:**

- No source changes.

- [ ] Create a feature branch from the current branch.

Run:

```bash
git switch -c feat/tv-phone-iptv-ui-redesign
```

Expected: new branch is active.

- [ ] Run baseline tests.

Run:

```bash
./gradlew testDebugUnitTest
```

Expected: existing unit tests pass before implementation begins.

- [ ] Run a baseline debug build.

Run:

```bash
./gradlew assembleDebug
```

Expected: debug APK builds successfully. If native build artifacts fail for an environment reason, record the exact failure before editing source.

## Task 1: UI Mode And Rotation Foundation

**Requirements:** R1-R4, R27-R30

**Files:**

- Create: `app/src/main/java/com/gamaxtersnow/mytv/ui/AppUiMode.kt`
- Create: `app/src/test/java/com/gamaxtersnow/mytv/ui/AppUiModeTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/gamaxtersnow/mytv/MainActivity.kt`

- [ ] Add `AppUiMode` with three effective modes: `TV`, `PHONE_PORTRAIT`, `PHONE_LANDSCAPE`.

Direction:

```kotlin
enum class AppUiSurface {
    TV,
    PHONE_PORTRAIT,
    PHONE_LANDSCAPE
}

data class AppUiMode(
    val surface: AppUiSurface,
    val isTelevision: Boolean,
    val isLandscape: Boolean
)
```

The detector should take simple inputs that are easy to unit test: has Leanback feature, smallest width, orientation, and touch availability. Keep Android framework calls at the edge, ideally in a factory method.

- [ ] Add tests covering:

  - Leanback device in landscape resolves to `TV`.
  - Touch phone in portrait resolves to `PHONE_PORTRAIT`.
  - Touch phone in landscape resolves to `PHONE_LANDSCAPE`.
  - Large touch tablet without Leanback should not automatically become TV unless the detector rule explicitly says so.

- [ ] Update `AndroidManifest.xml`.

Required direction:

```xml
android:configChanges="orientation|screenSize|keyboardHidden"
```

Remove `android:screenOrientation="landscape"` from `MainActivity` so phones can rotate.

- [ ] Add `MainActivity.onConfigurationChanged` and an internal `currentUiMode`.

It should refresh fragment layout state without recreating playback. Start with methods such as `playerFragment.onUiModeChanged(mode)`, `channelPanelFragment.onUiModeChanged(mode)`, and equivalent hooks for settings/EPG where needed.

- [ ] Run tests.

Run:

```bash
./gradlew testDebugUnitTest --tests 'com.gamaxtersnow.mytv.ui.AppUiModeTest'
```

Expected: new UI mode tests pass.

## Task 2: EPG XMLTV Cache Repository

**Requirements:** R31-R37, R4, R6, R11, R12, R15-R18, R26

**Files:**

- Create: `app/src/main/java/com/gamaxtersnow/mytv/epg/EpgCacheManager.kt`
- Create: `app/src/main/java/com/gamaxtersnow/mytv/epg/XmlTvParser.kt`
- Create: `app/src/main/java/com/gamaxtersnow/mytv/epg/EpgRepository.kt`
- Create: `app/src/main/java/com/gamaxtersnow/mytv/epg/EpgStatus.kt`
- Create: `app/src/test/java/com/gamaxtersnow/mytv/epg/XmlTvParserTest.kt`
- Create: `app/src/test/java/com/gamaxtersnow/mytv/epg/EpgCacheManagerTest.kt`
- Create: `app/src/test/java/com/gamaxtersnow/mytv/epg/EpgRepositoryTest.kt`
- Modify: `app/src/main/java/com/gamaxtersnow/mytv/Request.kt`
- Modify: `app/src/main/java/com/gamaxtersnow/mytv/SP.kt`
- Modify: `app/src/main/java/com/gamaxtersnow/mytv/MainActivity.kt`

- [ ] Add XMLTV parser support.

Parse at least:

  - `<channel id="..."><display-name>...</display-name></channel>`
  - `<programme start="yyyyMMddHHmmss Z" stop="yyyyMMddHHmmss Z" channel="..."><title>...</title></programme>`

The parser output should keep channel id, display names, programme title, start timestamp, and stop timestamp.

- [ ] Add parser tests.

Scenarios:

  - Parses channel display names.
  - Parses programme start/stop times with `+0800`.
  - Associates programme rows with the XMLTV channel id.
  - Ignores malformed programme rows without crashing.

- [ ] Add `EpgCacheManager`.

Required behavior:

  - Save raw XMLTV bytes or text under app-private files.
  - Track source key, successful update time, and covered date range.
  - Load the latest usable cache first.
  - Keep old cache when a refresh fails.
  - Delete stale files older than the retained window.

- [ ] Add cache tests.

Scenarios:

  - Missing cache returns no data and unavailable status.
  - Saved cache can be loaded back.
  - Failed refresh does not remove prior cache.
  - Stale cleanup keeps current files and removes old files.

- [ ] Add `EpgRepository`.

Responsibilities:

  - Load cache at app startup.
  - Refresh in background when last successful update is older than the selected interval.
  - Support manual refresh from settings.
  - Build an in-memory index keyed by channel id and normalized display name.
  - Expose `EpgStatus` for UI.

- [ ] Add repository tests.

Scenarios:

  - Cache-first load makes programmes available before network refresh.
  - Background refresh replaces index after successful download.
  - Refresh failure leaves previous index and marks status as stale/error.
  - Channel name normalization matches simple variants such as `CCTV1` and `CCTV-1`.

- [ ] Wire startup load in `MainActivity`.

Start with cached EPG before or alongside playlist loading. Refresh in the background without blocking playback startup.

- [ ] Add settings persistence in `SP`.

Track at minimum:

  - EPG last successful update time.
  - EPG source key.
  - EPG auto refresh interval.

- [ ] Run EPG tests.

Run:

```bash
./gradlew testDebugUnitTest --tests 'com.gamaxtersnow.mytv.epg.*'
```

Expected: parser, cache, and repository tests pass.

## Task 3: Shared Programme Summary Model

**Requirements:** R31-R37, R4, R6, R11, R12, R15-R18, R26

**Files:**

- Create: `app/src/main/java/com/gamaxtersnow/mytv/models/ProgramSummary.kt`
- Create: `app/src/test/java/com/gamaxtersnow/mytv/models/ProgramSummaryTest.kt`
- Modify: `app/src/main/java/com/gamaxtersnow/mytv/models/TVListViewModel.kt`
- Modify: `app/src/test/java/com/gamaxtersnow/mytv/models/ChannelPanelStateTest.kt`

- [ ] Add `ProgramSummary`.

Direction:

```kotlin
data class ProgramSummary(
    val currentTitle: String = "",
    val currentStartTimeText: String = "",
    val currentEndTimeText: String = "",
    val nextTitle: String = "",
    val nextStartTimeText: String = "",
    val progressPercent: Int? = null,
    val hasEpg: Boolean = false
)
```

Keep it UI-friendly so layouts do not parse timestamps.

- [ ] Add a builder that converts a sorted EPG list plus current timestamp into `ProgramSummary`.

Test scenarios:

  - Empty EPG returns `hasEpg=false` and blank text.
  - Current programme before next programme returns current and next titles.
  - Progress clamps between `0` and `100`.
  - Missing next programme leaves `nextTitle` blank.

- [ ] Extend `ChannelPanelRow`.

Replace or supplement `currentProgram: String` with `programSummary: ProgramSummary`. Keep a compatibility accessor if it minimizes churn in `ChannelPanelFragment`.

- [ ] Update `TVListViewModel.toChannelPanelSource()` to use `ProgramSummary`.

Use the `EpgRepository` in-memory index or already-populated `TVViewModel` EPG data. Do not introduce per-row network work here.

- [ ] Update `ChannelPanelStateTest`.

Add assertions for current title, next title, progress, and blank EPG degradation.

- [ ] Run model tests.

Run:

```bash
./gradlew testDebugUnitTest --tests 'com.gamaxtersnow.mytv.models.*'
```

Expected: all model tests pass.

## Task 4: Normal Playback Overlay

**Requirements:** R5-R9, R25, R27-R30

**Files:**

- Create: `app/src/main/res/layout/player_daily_overlay.xml`
- Modify: `app/src/main/res/layout/player.xml`
- Modify: `app/src/main/java/com/gamaxtersnow/mytv/PlayerFragment.kt`
- Modify: `app/src/main/java/com/gamaxtersnow/mytv/PlayerUiController.kt`
- Modify: `app/src/main/java/com/gamaxtersnow/mytv/InfoFragment.kt`
- Modify: `app/src/main/java/com/gamaxtersnow/mytv/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values/dimens.xml`

- [ ] Add `player_daily_overlay.xml`.

It should include:

  - Channel number and name.
  - Current programme and time range.
  - Progress bar.
  - Next programme.
  - Three actions: channel list, programme guide, settings.

TV mode should visually match Pencil `LdkiC`: low bottom overlay plus compact quick actions. Phone portrait should match `F1Zimz`: bottom control drawer. Phone landscape should use a lower-height overlay that keeps video dominant.

- [ ] Update `player.xml`.

Include `player_daily_overlay.xml` as the normal overlay. Keep existing status/control/performance includes, but default them to hidden and treat them as advanced/debug panels.

- [ ] Update `PlayerUiController`.

Responsibilities:

  - Bind daily overlay data from `TVViewModel` and `ProgramSummary`.
  - Apply `AppUiMode` layout changes.
  - Manage overlay show/hide timeout.
  - Keep debug/performance toggles separate from daily overlay.

- [ ] Update `PlayerFragment`.

When `play(tvViewModel)` is called, update daily overlay content in addition to starting playback. On UI mode changes, re-apply overlay layout without reinitializing the player.

- [ ] Update `MainActivity` input routing.

Expected behavior:

  - TV center/enter toggles playlist or daily overlay according to current product decision.
  - TV right opens settings only when no other overlay is open.
  - Phone single tap shows playback controls.
  - Phone control actions open playlist, EPG, or settings.

- [ ] Keep `InfoFragment` only if it still has a clear role.

Preferred direction: either fold its channel/programme behavior into daily playback overlay or make it a thin compatibility wrapper that delegates to `PlayerUiController`.

- [ ] Test manually.

Scenarios:

  - Start playback and confirm default state is visually quiet.
  - Invoke overlay on TV/landscape and confirm video remains primary.
  - Invoke overlay on phone portrait and confirm bottom controls are reachable.
  - Rotate phone while playing and confirm playback does not restart.

## Task 5: Playlist With Compact EPG

**Requirements:** R10-R14, R23, R24, R26, R29

**Files:**

- Modify: `app/src/main/java/com/gamaxtersnow/mytv/ChannelPanelFragment.kt`
- Modify: `app/src/main/res/layout/channel_panel.xml`
- Modify: `app/src/main/res/layout/channel_panel_row.xml`
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/gamaxtersnow/mytv/models/ChannelPanelStateTest.kt`

- [ ] Update row UI to show compact EPG.

Every row with EPG should show current programme and time range. The selected row should show progress and next programme. Rows without EPG should collapse cleanly to channel identity.

- [ ] Update `ChannelPanelFragment` layout configuration.

Mode behavior:

  - TV: left-side panel with group rail and channel list, matching Pencil `w9xLg`.
  - Phone portrait: bottom drawer with group tabs and compact rows, matching `nBgFd`.
  - Phone landscape: side panel or low bottom panel, chosen during implementation based on available screen space; it must not obscure most of the video.

- [ ] Preserve remote behavior.

Do not regress:

  - Up/down moves rows.
  - Left/right moves groups.
  - Confirm plays selected channel.
  - Back closes the panel.
  - Auto-hide still runs.

- [ ] Add touch behavior for phone.

Tapping group tabs changes group. Tapping a row plays that channel. Tapping outside closes the panel.

- [ ] Run model tests.

Run:

```bash
./gradlew testDebugUnitTest --tests 'com.gamaxtersnow.mytv.models.ChannelPanelStateTest'
```

Expected: grouping, selected state, and programme summary tests pass.

- [ ] Manual checks.

  - TV playlist is navigable by remote only.
  - Phone portrait playlist fits `390 x 844` proportions.
  - Missing EPG does not leave empty rows or clipped text.

## Task 6: Dedicated EPG Panel

**Requirements:** R15-R18, R23, R24, R26, R29

**Files:**

- Create: `app/src/main/java/com/gamaxtersnow/mytv/EpgPanelFragment.kt`
- Create: `app/src/main/res/layout/epg_panel.xml`
- Create: `app/src/main/res/layout/epg_panel_row.xml`
- Create: `app/src/test/java/com/gamaxtersnow/mytv/models/EpgPanelStateTest.kt`
- Modify: `app/src/main/java/com/gamaxtersnow/mytv/MainActivity.kt`
- Modify: `app/src/main/java/com/gamaxtersnow/mytv/models/TVListViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] Add an EPG state builder.

It can reuse `ChannelPanelGroup`/row data if that remains clean, or create a small EPG-specific state model if programme browsing needs more fields. Keep it testable without Android framework classes.

- [ ] Add `EpgPanelFragment`.

Mode behavior:

  - TV: browse EPG while leaving part of video visible, matching Pencil `bi8Au`.
  - Phone portrait: bottom drawer, matching `M5m6U`.
  - Phone landscape: side panel or shorter bottom panel to preserve video.

- [ ] Show source status.

Display EPG source, cache freshness, covered date range, and availability from `EpgStatus`. If real match rate is not yet implemented, do not show a fake percentage.

- [ ] Wire entry points.

Open EPG from:

  - Playback overlay programme-guide action.
  - Phone controls programme-guide action.
  - TV remote/menu path chosen in Task 4.

- [ ] Add tests.

Scenarios:

  - Channels with current and next programme produce visible EPG rows.
  - Empty EPG produces channel-only rows.
  - Group order remains stable.
  - Current playing channel is selected when EPG opens.

- [ ] Manual checks.

  - EPG opens without stopping playback.
  - EPG closes with back/tap outside.
  - Phone portrait and landscape do not clip row text.

## Task 7: Settings Redesign

**Requirements:** R19-R22, R24, R29

**Files:**

- Modify: `app/src/main/java/com/gamaxtersnow/mytv/SettingFragment.kt`
- Modify: `app/src/main/res/layout/setting.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] Rework `setting.xml` around the Pencil structure.

Mode behavior:

  - TV: centered panel with left category navigation and right content area, matching `G3ddD`.
  - Phone portrait: bottom drawer with segmented categories, matching `dN8Y9`.
  - Phone landscape: side panel or low-height panel so video remains visible.

- [ ] Preserve existing settings behavior.

Do not regress:

  - Remote playlist URL editing.
  - Immediate playlist update.
  - Cached channel count and last update display.
  - Channel reversal switch.
  - Channel number switch.
  - Time display switch.
  - Boot startup switch.
  - Exit app action.

- [ ] Add EPG status and manual refresh placement.

Show source, last successful update, covered date range, availability/stale state, and a manual refresh action wired to `EpgRepository`.

- [ ] Preserve focus and auto-hide behavior.

TV focus should keep the setting dialog alive. Touch interaction should not unexpectedly close the drawer while editing URL.

- [ ] Manual checks.

  - TV settings category navigation works by remote.
  - Phone portrait URL input is not clipped by keyboard.
  - Phone landscape settings does not obscure most of the video.

## Task 8: Phone Gesture And Overlay Routing

**Requirements:** R23-R30

**Files:**

- Modify: `app/src/main/java/com/gamaxtersnow/mytv/MainActivity.kt`
- Modify: `app/src/main/java/com/gamaxtersnow/mytv/ChannelPanelFragment.kt`
- Modify: `app/src/main/java/com/gamaxtersnow/mytv/EpgPanelFragment.kt`
- Modify: `app/src/main/java/com/gamaxtersnow/mytv/SettingFragment.kt`
- Modify: `app/src/main/java/com/gamaxtersnow/mytv/PlayerFragment.kt`

- [ ] Centralize overlay exclusivity in `MainActivity`.

At most one of playlist, EPG, settings, or advanced/debug panel should be active. Opening one should close incompatible overlays.

- [ ] Define phone gestures.

Recommended behavior:

  - Single tap toggles playback overlay.
  - Swipe up/down changes channel only when no overlay is open.
  - Overlay buttons open playlist, EPG, or settings.
  - Back closes the active overlay before exiting.

- [ ] Define TV remote behavior.

Keep current efficient behavior:

  - Up/down changes channel when no panel is open.
  - Center/enter opens playlist.
  - Left opens playlist from playback.
  - Right or menu opens settings.
  - A programme-guide action opens EPG.

- [ ] Handle rotation state.

When `onConfigurationChanged` fires:

  - Do not call `play()` again.
  - Re-apply current UI mode to visible overlays.
  - Preserve selected group/row in playlist and EPG.
  - Preserve setting category.

- [ ] Manual checks.

  - Rotate with playback overlay open.
  - Rotate with playlist open.
  - Rotate with EPG open.
  - Rotate with settings open and URL input focused.

## Task 9: Advanced Player Debug Relocation

**Requirements:** R9, R22

**Files:**

- Modify: `app/src/main/java/com/gamaxtersnow/mytv/PlayerUiController.kt`
- Modify: `app/src/main/res/layout/player.xml`
- Modify: `app/src/main/res/layout/player_status_overlay.xml`
- Modify: `app/src/main/res/layout/player_control_panel.xml`
- Modify: `app/src/main/res/layout/performance_monitor_panel.xml`
- Modify: `app/src/main/java/com/gamaxtersnow/mytv/SettingFragment.kt`
- Modify: `app/src/main/res/layout/setting.xml`

- [ ] Keep advanced/debug UI available but hidden from normal playback.

The normal user should not see player type, performance mode, FPS, buffer, bitrate, latency, or export controls unless they intentionally open an advanced/debug path.

- [ ] Add an advanced entry in settings or a long-press/menu path.

Prefer settings, because it is discoverable and keeps debug controls out of playback.

- [ ] Confirm advanced controls still work.

Existing behavior to preserve:

  - Apply player settings.
  - Reset player settings.
  - Toggle performance details.
  - Clear performance alerts.
  - Export report action placeholder.

- [ ] Manual checks.

  - Normal playback overlay has no debug metrics.
  - Advanced panel can still be opened intentionally.
  - Closing advanced panel returns to playback without restarting video.

## Task 10: Visual Polish And Resource Cleanup

**Requirements:** R1-R37

**Files:**

- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: affected layout XML files from previous tasks.

- [ ] Consolidate repeated colors and dimensions.

Use shared names for IPTV overlay background, focus color, accent, text primary, text secondary, row height, panel padding, and phone drawer heights.

- [ ] Move hardcoded user-facing strings into `strings.xml`.

At minimum include playback actions, EPG labels, settings category names, update/status labels, and empty EPG text.

- [ ] Check text fitting.

Manual scenario strings:

  - Long channel name.
  - Long programme title.
  - Empty EPG.
  - Stale EPG cache.
  - EPG refresh failure.
  - Update failure.
  - Long remote playlist URL.

- [ ] Keep cards and panels restrained.

Follow the Pencil direction: dark translucent playback overlays, teal focus/accent, white phone drawers, compact radii. Avoid nested card-in-card compositions.

## Task 11: Verification

**Requirements:** R1-R37

**Files:**

- No new production files expected.

- [ ] Run all unit tests.

Run:

```bash
./gradlew testDebugUnitTest
```

Expected: all unit tests pass.

- [ ] Build debug APK.

Run:

```bash
./gradlew assembleDebug
```

Expected: APK builds successfully.

- [ ] Manual TV verification.

Use a TV/emulator or large landscape device with remote input:

  - Start playback.
  - Open playback overlay.
  - Open playlist and switch channels.
  - Open EPG.
  - Open settings.
  - Confirm back closes overlays in order.

- [ ] Manual phone portrait verification.

Use a phone/emulator:

  - Start playback in portrait.
  - Tap to show playback controls.
  - Open playlist, EPG, and settings.
  - Edit playlist URL field.
  - Confirm controls fit without forced landscape.

- [ ] Manual phone landscape verification.

Rotate while playing:

  - Playback continues.
  - Video area remains dominant.
  - Playlist/EPG/settings adapt to landscape.
  - Overlay state and selected item are preserved.

- [ ] Regression checks.

  - Remote playlist update still refreshes channel list.
  - Number input channel overlay still works when enabled.
  - Channel reversal still works.
  - Time display setting still works.
  - Boot startup setting still persists.
  - EPG cache loads while offline after one successful refresh.
  - Manual EPG refresh failure preserves prior programme data.

## Requirement Traceability

- R1-R4: Tasks 1, 4, 5, 6, 7, 8, 10
- R5-R9: Tasks 4, 9
- R10-R14: Tasks 3, 5
- R15-R18: Tasks 2, 3, 6
- R19-R22: Tasks 7, 9
- R23-R26: Tasks 5, 6, 8
- R27-R30: Tasks 1, 4, 5, 6, 7, 8, 11
- R31-R37: Tasks 2, 3, 6, 7, 10, 11

## Risks

- Rotation can restart playback if `MainActivity` or `PlayerFragment` lifecycle is mishandled. Mitigation: Task 1 adds config-change handling before UI work, and Task 11 verifies rotation while playing.
- The broad UI scope can become too large for one commit. Mitigation: implement task-by-task with separate commits after tests/builds.
- EPG match rate may not exist yet as real data. Mitigation: show only trustworthy freshness/status data until match-rate plumbing exists.
- EPG XMLTV files can be large. Mitigation: persist compressed/raw source once, parse off the main thread, and keep only indexed programme records needed by UI in memory.
- Channel matching can be imperfect. Mitigation: start with deterministic id/display-name normalization, expose status clearly, and avoid blocking playback when no match exists.
- Existing debug panels may be relied on during development. Mitigation: relocate them behind an advanced path instead of deleting them.

## Suggested Commit Sequence

1. `feat: add responsive ui mode detection`
2. `feat: add epg xmltv cache repository`
3. `feat: add shared programme summary state`
4. `feat: redesign playback overlay`
5. `feat: add compact epg playlist rows`
6. `feat: add responsive epg panel`
7. `feat: redesign responsive settings`
8. `feat: preserve overlays across phone rotation`
9. `chore: move player diagnostics behind advanced settings`
10. `chore: polish iptv ui resources`
