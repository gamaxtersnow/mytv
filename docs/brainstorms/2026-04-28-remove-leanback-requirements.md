---
date: 2026-04-28
topic: remove-leanback
---

# Remove Leanback From the TV/Phone IPTV App

## Problem Frame

The app now uses custom playback, playlist, EPG, and settings surfaces based on the Pencil redesign. Android Leanback remains in the codebase mostly through the legacy main channel browser, but that browser also owns important non-UI responsibilities: channel list construction, current channel state, playback switching, EPG attachment, and startup playback triggers.

Keeping those responsibilities inside a Leanback `BrowseSupportFragment` makes the new custom UI harder to reason about and prevents the app from becoming lightweight and framework-independent. The goal is to remove Leanback completely while preserving TV remote ergonomics, phone behavior, startup playback, playlist refresh, and EPG data flow.

## Requirements

**Migration Strategy**

- R1. The Leanback removal must happen in two stages: first move core channel/playback responsibilities out of Leanback, then delete the Leanback UI and dependency.
- R2. Stage one must leave the app behaviorally equivalent from the user's perspective while making Leanback non-essential to playback, channel switching, playlist, EPG, and settings flows.
- R3. Stage two must remove all Leanback source usages and the Gradle dependency after stage one has proven the replacement path works.

**Channel and Playback Ownership**

- R4. Channel list construction from `TVList.list` must be owned by a non-UI coordinator or view-model-like component instead of a Fragment subclass.
- R5. Current channel state must have one source of truth that is independent of Leanback selected row/item state.
- R6. Playback commands must remain available as simple operations: play by channel id, previous channel, next channel, and refresh/reload channel list.
- R7. Startup playback must continue to select the persisted channel when valid and fall back safely when the persisted position is invalid.
- R8. Channel switching must preserve existing behavior for invalid channels, duplicated URLs, empty URLs, and error notifications.

**EPG and Playlist Flow**

- R9. Cached EPG data must continue to attach to channels during channel list construction without requiring any Leanback UI object.
- R10. Existing FEPG/request-based updates for channels that need them must continue to run after startup or channel reload.
- R11. Remote playlist refresh from settings must rebuild the shared channel state used by playback, playlist, and EPG panels.
- R12. Playlist and EPG panels must consume the new shared channel state directly, not through a hidden or retained Leanback fragment.

**UI Replacement**

- R13. The app must not create or hide a legacy main browser fragment as part of normal startup after stage one is complete.
- R14. TV remote behavior must remain efficient without Leanback focus helpers: up/down changes channel when no panel is open, center/left opens playlist, right/menu opens settings, and guide/help opens EPG.
- R15. Custom playlist, EPG, settings, playback overlay, number overlay, time overlay, and error display must remain the visible UI surfaces after Leanback is removed.
- R16. Leanback `ErrorSupportFragment` must be replaced with a lightweight custom error surface before the Leanback dependency is removed.

**Cleanup**

- R17. `MainFragment`, `CardPresenter`, Leanback card styles, Leanback imports, and the `androidx.leanback` Gradle dependency must be removed in the final cleanup stage.
- R18. The Android TV launcher/device declarations in `AndroidManifest.xml` may remain if they are still needed for TV distribution or device classification; they are not considered Leanback library usage.
- R19. The final codebase must pass a source search with no app code references to Leanback classes or resources.

## Success Criteria

- The app starts playback without instantiating `MainFragment`.
- Playback, previous/next channel, numeric channel entry, playlist, EPG panel, settings, time overlay, error display, remote playlist refresh, and EPG refresh still work.
- `rg "androidx\\.leanback|BrowseSupportFragment|ImageCardView|ErrorSupportFragment|ArrayObjectAdapter|ListRow|Presenter"` returns no production source usage after final cleanup.
- `app/build.gradle` no longer contains the Leanback dependency.
- Unit tests cover the new non-UI channel/playback coordinator behavior.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest` passes.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug` passes.

## Scope Boundaries

- Do not redesign the playback, playlist, EPG, or settings visuals as part of this work.
- Do not introduce Compose, Navigation, or a new app architecture framework.
- Do not change the player engine or stream selection logic beyond what is required to move ownership out of Leanback.
- Do not remove Android TV manifest support solely because Leanback is removed.
- Do not rewrite EPG repository/cache behavior; only reconnect it to the new channel ownership path.

## Key Decisions

- Use a two-stage migration: this minimizes regression risk by proving the new coordinator path before deleting the old UI.
- Keep the replacement lightweight: the current app is already FragmentActivity plus custom fragments, so a small coordinator/view-model-like layer is enough.
- Treat Leanback as removable UI, not as the TV support strategy: TV support should come from custom remote handling, focusable XML views, and manifest/device-mode handling.
- Preserve existing custom surfaces: the Pencil redesign has already shifted the product direction away from a card browser toward playback-first overlays and panels.

## Dependencies / Assumptions

- `MainFragment` currently owns business behavior that must be extracted before deletion.
- `ErrorFragment` currently depends on Leanback and must be replaced before removing the dependency.
- The current custom playlist and EPG panels are sufficient as the primary browse surfaces once their data source no longer comes through `MainFragment`.

## Outstanding Questions

### Resolve Before Planning

- None.

### Deferred to Planning

- [Affects R4-R12][Technical] Decide the exact type/name and lifecycle owner for the replacement channel/playback coordinator.
- [Affects R8][Technical] Decide whether duplicated URL suppression belongs in the new coordinator or in the player layer.
- [Affects R16][Technical] Decide whether the replacement error surface should be a Fragment, lightweight overlay view, or reuse the existing `ErrorFragment` name with a new base class.

## Next Steps

-> `/ce:plan docs/brainstorms/2026-04-28-remove-leanback-requirements.md` for structured implementation planning.
