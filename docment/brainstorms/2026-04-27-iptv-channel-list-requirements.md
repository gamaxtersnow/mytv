---
date: 2026-04-27
topic: iptv-channel-list
---

# IPTV Channel List Experience

## Problem Frame

The app currently presents channels as large Leanback-style media cards grouped into horizontal rows. That pattern works for video-on-demand browsing, but it does not match common IPTV and set-top-box behavior where users expect fast channel switching, numbered channel lists, visible grouping, and a playback-first overlay.

The goal is to redesign the channel playlist experience so it feels like a live TV/IPTV app while remaining convenient on both TV remote controls and phones.

## Requirements

**Playback-First Channel Panel**
- R1. The main playback surface remains full screen while the channel list appears as a temporary overlay.
- R2. On TV-sized landscape screens, the overlay uses a side-panel layout with a narrow group column and a wider channel list column.
- R3. On phone-sized or portrait screens, the same channel information is shown as a touch-friendly bottom sheet or drawer instead of a TV side panel.
- R4. Opening the channel panel positions the list on the currently playing channel.

**IPTV Channel List Content**
- R5. Channels are displayed as compact rows, not poster cards.
- R6. Each channel row shows at least channel number, channel name, and current playing state.
- R7. When available, each channel row also shows logo and current program text from EPG data.
- R8. The currently playing channel is visually distinct from the focused or touched channel.
- R9. Channel numbers use a stable display format such as `001`, `002`, `003` so numeric entry and list scanning match IPTV conventions.

**Grouping and Discovery**
- R10. M3U `group-title` values remain the source of normal channel groups.
- R11. The panel includes an `All` group that lists every playable channel.
- R12. The panel includes lightweight virtual groups for `Favorites` and `Recent` if enough local state exists to support them without blocking the first release.
- R13. Empty or missing M3U groups are shown under a fallback group such as `Custom`.

**TV Remote Interaction**
- R14. While the channel panel is closed, up/down or channel up/down continues to switch channels directly.
- R15. Pressing OK/center opens or closes the channel panel.
- R16. While the panel is open, up/down moves through channels, left/right changes group focus, OK plays the focused channel, and Back closes the panel without exiting playback.
- R17. Numeric key entry continues to select channels by channel number and keeps the existing short-lived number overlay behavior.
- R18. The channel panel auto-hides after inactivity only when it does not interrupt an active selection flow.

**Phone Touch Interaction**
- R19. A single tap exposes touch controls without disrupting playback.
- R20. A channel-list affordance opens the phone channel drawer.
- R21. Phone users can switch groups with horizontal tabs and switch channels with vertical scrolling and tapping.
- R22. Selecting a channel on phone starts playback and closes or collapses the drawer.

**Continuity and Error Handling**
- R23. Existing playback behavior, remote playlist loading, channel cache refresh, numeric channel entry, and settings behavior remain intact.
- R24. If a selected channel cannot play because it has no usable URL, the app reports the existing error behavior instead of silently closing the panel.
- R25. Playlist updates preserve the best available current-channel position; if the channel no longer exists, the app falls back to the first playable channel.

## Success Criteria

- Users can open a channel list from playback, identify the current channel, switch to another channel, and return to fullscreen playback using only a TV remote.
- Users can perform the same channel switch flow on a phone with tap and scroll gestures.
- The channel list visually reads as an IPTV/live TV channel list rather than a video-on-demand gallery.
- Existing channel up/down and numeric channel switching continue to work.
- Large M3U playlists remain scannable because channels are shown in dense rows with clear group navigation.

## Scope Boundaries

- Do not build a full timeline-style EPG grid in this iteration.
- Do not replace the remote playlist parser or playlist cache format unless implementation reveals a small compatibility change is required.
- Do not add cloud sync, account state, or cross-device favorites.
- Do not add advanced search as a first-release requirement.
- Do not redesign playback engine behavior.

## Key Decisions

- Use a playback overlay instead of a standalone browsing page: This matches IPTV expectations and avoids making channel switching feel like leaving live TV.
- Use compact channel rows instead of cards: Rows scale better for large playlists and make channel numbers, group names, and playing state easier to scan.
- Treat TV and phone layout differently while sharing the same channel data: Remote-control navigation and touch navigation have different ergonomics, but should not require separate playlist semantics.
- Keep EPG as secondary row metadata: EPG improves scan quality when present, but missing EPG should not block channel browsing or playback.

## Dependencies / Assumptions

- Existing M3U parsing already preserves channel names, logos, group titles, and playable URLs.
- Existing TV list construction already provides continuous channel ids that can be used as the basis for display numbers.
- Existing EPG coverage is partial, so the UI must tolerate missing current program text.

## Outstanding Questions

### Resolve Before Planning

- None.

### Deferred to Planning

- [Affects R2, R3][Technical] Decide whether the current Leanback `BrowseSupportFragment` can be adapted cleanly or should be replaced with a custom overlay fragment.
- [Affects R12][Technical] Determine whether favorites and recents can be supported with existing local storage patterns in the first implementation pass.
- [Affects R18][Technical] Define exact inactivity timeout behavior after inspecting current fragment hide timers.

## Next Steps

-> /ce:plan for structured implementation planning.
