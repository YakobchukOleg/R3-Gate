# WorkMeter SOLO — Design System & UX Contract

**Version:** 1.0  
**Status:** Approved product/design direction; implementation is incremental, not a claim of current feature completeness  
**Date:** 2026-11-03  
**Repository:** `YakobchukOleg/R3-Gate`  
**Primary language of application UI:** Polish  
**Owners:** Chat 3 (UI/design implementation), Chat 4 (independent QA); Chats 1/2/5 consume the contract.

> **Product objective:** maximum useful functions with minimum interaction. The app should observe, propose and let the user confirm relevant changes. Design must reduce taps without hiding existing functionality or inventing automatic certainty.

## 1. Scope and authority

This document defines shared UX, visual themes, navigation, widget layouts, interaction contracts, acceptance criteria and team ownership for WorkMeter SOLO. Applies to existing `main` code and to work in parallel branches **when integrated**.

This is a **design specification**, not a statement that all features described here already exist. Do not confuse proposed P2–P4 concepts, experimental GPS capabilities and merged Android functionality. The code, tests and actual branch HEAD remain authoritative for implementation status.

Do not change `applicationId = com.r3gate.app`, existing signing compatibility, legacy `r3_gate` preferences or saved identifiers as a consequence of redesign. Existing shifts, finances, photos, objects and materials must remain readable.

## 2. Core principles

1. **Context first.** Home displays current work state, elapsed time, confirmed object, location service status and exactly one visually dominant primary action.
2. **Progressive disclosure.** Common operations are immediately reachable; complex settings appear in secondary surfaces. No function is removed merely for minimalist appearance.
3. **Minimum taps.** One tap for a safe GPS proposal acknowledgement; direct camera entry from Home/widget; use confirmed current object by default; avoid repeated object selection.
4. **Human confirmation.** GPS/accelerometer output is provisional evidence. It NEVER silently overwrites manual/confirmed work stages, shift totals, pay rates or earnings. Core validates before accepting.
5. **Offline first.** Essential data entry and time tracking do not require connectivity. Clearly distinguish local save, queued upload, cloud backup, and device-to-device sync.
6. **Accessible and robust.** Clear text, visible focus, comprehensible errors, scalable text, large touch targets and status meaning independent of color.
7. **Consistent Polish copy.** A single vocabulary for states/actions; never label an unimplemented concept as a functional service.
8. **Privacy.** Location/sensor collection is transparent and user controlled; no hidden transmission of raw GPS. Personal time and closed shifts are handled per explicit user-facing settings.

## 3. Theme system

All app screens and widgets must use **semantic tokens**, not hardcoded individual color choices. One component tree, multiple palettes. Three user-selectable themes:

| Token | Slate & Sage (default) | Nordic Blue | Graphite Dark |
|---|---|---|---|
| `background` | `#F5F7F7` | `#F7F8FA` | `#151D24` |
| `surface` | `#FFFFFF` | `#FFFFFF` | `#26343E` |
| `header` | `#293F49` | `#1F2937` | `#202D36` |
| `primary` | `#32866E` | `#2563EB` | `#54C6A1` |
| `textPrimary` | `#293F49` | `#1F2937` | `#FFFFFF` |
| `textSecondary` | `#71868D` | `#718096` | `#B9CAD0` |
| `border` | `#DEE7E6` | `#E2E8F0` | `#40515C` |

Accessibility note: these are **starting design tokens**. Validate actual foreground/background pairs against WCAG 2.2 contrast, including primary-button text. For example, dark-theme mint requires dark text where white contrast is insufficient. Small status text cannot rely on pale accents over light surfaces.

### Work-stage semantic colors

| State | Base indicator |
|---|---|
| `Obiekt` (on-site work) | `#32866E` green |
| `Przejazd` (travel) | `#98A7B0` grey |
| `Zakupy` (shopping) | `#DBA6AD` soft pink |
| `Prywatnie` (personal, non-billable) | `#C9AF78` sand |
| Destructive / error | `#B94C5A` red |

These indicate activity consistently across all themes, charts, timelines, and widgets. Use accessible labels/icons and suitable foreground text; never rely on stage color alone. Do not color-fill entire screens to indicate a stage.

### Presentation guidelines

- Soft Industrial Minimalism: restrained typography, neutral surfaces, clear grouping, minimal shadows and restrained accents.
- Only one **primary emphasis** action in the active context.
- Prefer 48dp-or-larger primary touch targets; ensure a touch target of at least 48×48dp wherever practical. Maintain separation and accessible alternatives.
- Use readable contrast, scalable font sizes, wrap long Polish labels, support smallest-width phone layouts, landscape, display scaling, and edge insets.
- Show states for pressed/disabled/loading/error/success. Don't put important actions behind color-only icons.
- Respect native Android back navigation and input conventions.
- Avoid unnecessary confirmation dialogs, except for destructive actions, possible data-loss scenarios and changes to authoritative work/financial records.

## 4. Navigation / information architecture

Proposed permanent top-level destinations:
1. `Start`: contextual shift dashboard, GPS suggestions, current object, quick-add.
2. `Obiekty`: object list/search, object-centric records, active/archive views.
3. `Mój czas`: shifts, history, timelines, charts and unresolved GPS suggestions.
4. `Finanse`: PIN-protected rates, earnings, payments and balances.
5. `Więcej`: reports, photos/materials cross-object views, notes, settings, exports/imports, Drive and P2–P4 extension concepts.

Migration must **preserve** the existing P1–P4 and six-tab feature access until equivalently reachable. This five-destination IA is a proposed target, not permission to remove working screens or silently break navigation.

Global `+` (where available) exposes `Zdjęcie`, `Notatka`, `Materiał`, `Zadanie`. On an object screen, object ID is prefilled; on Home, use *confirmed* active object. When unknown, offer recent objects / explicit selection, never a random location-based association.

## 5. Contextual Home: states and actions

Home must show:
- current stage with **text + indicator**;
- total workday time and current-stage elapsed time (label each to avoid ambiguity);
- confirmed object tag/name, or clearly `Nie przypisano obiektu`;
- GPS status (`aktywny`, `oczekiwanie`, `niedostępny`, `wyłączony`, error);
- prominent stage-dependent primary action;
- access to manual stage correction, shift close and quick-add; pending suggestion counter when needed.

| State | Primary-action concept | Secondary available actions |
|---|---|---|
| Closed day | `Rozpocznij dzień` | History, objects, settings |
| Working on object | `Rozpocznij przejazd` when appropriate | Zakupy, Prywatnie, add photo, edit stage, end day |
| Travel | `Potwierdź przyjazd` when there is a credible candidate; otherwise `Wybierz cel` | Zakupy, manual selection, correction, end day |
| Shopping | `Zakończ zakupy` | Attach receipt/material, correction |
| Personal | `Wznów pracę` | End day, correction |
| Open, unspecified stage | `Wybierz etap` | Manual work/travel/shopping, end day |

The interface must not display a false confirmed GPS arrival when evidence is weak. No automatic object selection for ambiguous nearby sites. Start-of-day need not require an object before the shift can be tracked where Core supports unassigned state.

**Primary action is state-driven, not a fixed color/button string.** The Core layer provides legal actions; UI renders them. Always offer discoverable manual fallback when GPS is disabled.

## 6. Balanced automation (variant B): GPS → proposal → confirmation

**Hard invariant:** location and motion data alone do not directly modify confirmed work time, wages or payment.

- Observations include GPS fixes, location quality, geofence dwell, trip evidence, accelerometer/motion hints, provider health and monitoring gaps.
- Low confidence: internal provisional observation only.
- Medium confidence: one pending suggestion in local review queue.
- High confidence: eligible suggestion plus actionable, non-spam notification; never authoritative until accepted.
- Suggested events: departure/trip start, arrival, trip stop, object visit, potential shop visit. A stop near a shop is not proof of purchasing.
- Event IDs remain stable on retry, restart, export/import; rejected events stay rejected and are not resurfaced as new duplicates.
- User choices: `Potwierdź`, `Później`, `Odrzuć`; accepted proposals must pass Core conflict checks. Batch accept is allowed **only** for a consistent non-overlapping timeline.
- Event timestamps come from reliable observation, not the time the notification was tapped. Show time provenance if uncertain.
- No assumed trip ending during GPS gap, clock rollback, coarse-only fix or Android background throttling.
- Coalesce redundant notifications; show remaining suggestions in `Do sprawdzenia`.
- GPS outage should degrade gracefully to manual work tracking, with actionable diagnostic status.

**Important:** possible GPS thresholds (dwell time/radius/motion evidence) are calibration candidates, not fixed product truth; validate on-device rather than hardcode arbitrary promises.

### Shift lifecycle

- When a user starts workday with GPS allowed, start tracking session under Android permissions.
- On day close, stop SOLO location requests/service and any unnecessary motion registrations; do not claim control of the phone's global Location toggle.
- No hidden tracking outside explicit user permission. Behavior for `Prywatnie` must be privacy-explicit; suppress work-related suggestions during personal time.
- Closed day can retain unresolved suggestions only as non-authoritative records; never silently revise completed shifts or payroll.

Existing GPS/Core event contract: `docs/gps-core-event-contract-v1.md`. Keep stable `eventId`, object ID and manual override precedence.

## 7. Smart camera / contextual capture

Target **camera reachable in one tap** from Home/widget (actual capture requires Android camera interaction). Once photographed:
- save original photo durably and locally before any network operation;
- suggest current *confirmed* object, capture time, optional description/status;
- auto-queue upload; show separate states `Lokalnie`, `Oczekuje`, `Wysyłanie`, `Wysłano`, `Błąd`;
- preserve original quality/EXIF, 16:9 fit preview without forced crop; avoid duplicate uploads;
- optionally `Dodaj opis`, `Zgłoś problem`, `Zmień obiekt`, `Dodaj notatkę`;
- never bind photo to guessed object without user confirmation.

Later proposals (not guaranteed implemented): OCR for serial numbers, voice descriptions and photo-to-task action.

## 8. Single object card

One object context with tabs or sections `Dzisiaj`, `Zadania`, `Zdjęcia`, `Materiały`, `Historia`; expose notes, travel and time records as appropriate.
- Object tag, name, city, current/archived state visible.
- Context is retained when creating linked photo/material/note/task; fewer repeated choices.
- Archived IDs remain in historical records but cannot be selected for new associations unless policy explicitly permits.
- Actions for archiving and record mutations remain safe and confirmation-aware.
- Avoid creating separate parallel object databases or business logic within UI.

See `docs/ui/core-object-integration-contract.md` in the UI branch for a proposed Core/UI gateway; its status is provisional.

## 9. Adaptive Android home-screen widget

One logical widget feature with responsive layouts based on launcher-provided bounds (`AppWidgetManager` / `RemoteViews`), not four independent business logic stacks.

Target layout tiers (Android launcher grid counts vary; actual dp size controls layout):
- **Mini ~2×1:** stage/timer + object tag when space allows; tap opens SOLO.
- **Compact ~2×2:** timer, stage/confirmed object and primary action if enough space.
- **Standard ~4×2 (default):** time, stage/object, one primary context action and quick camera/shopping control.
- **Expanded ~4×3:** standard content + GPS status, pending proposal count and succinct day distribution.

**Do not promise** that a 2×1 launcher box has room for all information or full tap targets; degrade gracefully, truncate secondary copy, not essential labels. The user's launcher controls resizing; in-app settings can show previews and provide help but may not directly set installed widget size.

Widget personalization:
- `Taki jak aplikacja` (inherit theme; default) OR separate Slate & Sage / Nordic Blue / Graphite Dark.
- Background **transparency control 0–100%**; specify clearly whether the UI refers to transparency or opacity and display correct percentage. Apply only to background, **not** foreground text or buttons.
- Options to show/hide GPS, object and additional quick actions, subject to minimum layout safety.
- Persist settings per widget instance ID where possible; multiple instances can differ.
- Preview each size/theme; resize and reconfiguration retain working actions.
- Protect existing PendingIntent routing, confirmed object labels, PIN policy, timer updates, boot recovery and widget accessibility.

Android widget is not an unrestricted live view: refresh frequency and background operations follow platform limits. Never promise a second-by-second timer for all launchers unless implemented/tested.

## 10. Settings UX

`Ustawienia → Wygląd`:
- `Motyw aplikacji`: Slate & Sage / Nordic Blue / Graphite Dark.
- `Motyw widżetu`: `Taki jak aplikacja` / one of three independent themes.
- `Rozmiar widżetu`: preview/help for 2×1, 2×2, 4×2, 4×3; actual installed dimensions controlled by launcher.
- Background transparency slider 0–100%; accurate wording and safe foreground contrast.
- Options for object/GPS/quick-action visibility, preview and `Przywróć domyślne`.

Preferences must persist after process death and reboot. Theme switching should preserve current screen and scroll where feasible and never reset active shift.

Other Settings retain GPS control, backup/export/import, Google Drive, PIN/wallet, SMS reports, language and About/version.

## 11. Offline, backup, import/export semantics

- `Zapisano lokalnie` != `Kopia w Google Drive gotowa` != `Zsynchronizowano między urządzeniami`.
- Local transactions are durable even offline; queued uploads retry safely without silently changing authoritative records.
- Include export date in user-visible filename for file and Google Drive exports. Do not overwrite without explicit consent.
- Import from file/Drive requires valid permissions and archive preflight/consistency validation.
- Preview restore conflicts; avoid silent destruction/duplicates. Preserve object IDs, attachments, stages, PIN and financial consistency where supported.
- Surface backup on Home/widget only for an actionable error; otherwise status belongs in dedicated settings.

## 12. Required usability scenarios and acceptance checks

Target tap counts (these are design goals, not a guarantee of existing APK behavior):
- Safe GPS proposal accept: **1**.
- Open camera from Home/widget: **1** to launch; shutter/system prompts extra.
- Quick-add under confirmed object: no redundant object selection.
- Shift close: discoverable, no more than a short review/confirmation.
- Full manual work recording remains possible with no GPS/internet.

QA must verify across three themes, device sizes/text scales and widget size tiers:
- no clipped content, minimum touch areas, correct color contrast, focus and screen reader labels;
- closed / work / travel / shopping / personal / unknown Home states;
- background/return-from-app restoration; no PIN leak into finance;
- duplicate GPS events, stale/uncertain location, competing geofences, Android permission and Doze changes;
- no GPS-induced confirmed time/pay changes; no overlap or duplicate shifts after accept;
- object context in photo/material/note flows;
- offline save, retry, ZIP integrity, imports and partial backups;
- widget 2×1/2×2/4×2/4×3 in actual launcher constraints, resizing, multiple instances, per-instance theme and opacity;
- exact commit SHA + successful CI is needed for a claim of a tested branch. JVM/lint ≠ emulator ≠ real-device validation.

## 13. Responsibility and integration workflow

| Chat | Branch | Authority / deliverable |
|---|---|---|
| 1 GPS | `feature/gps-watchdog-motion-geofence` | Sensor/location evidence, state, durable proposals, alert guard; no direct financial/work mutation |
| 2 Core | `feature/workmeter-core` | Stage state machine, validated transitions, object data contract and authoritative records |
| 3 UI & Widgets | `feature/workmeter-ui` | Visual tokens, themes, responsive widget layouts, UX screens and navigation |
| 4 QA | `feature/workmeter-qa` | Cross-theme and cross-feature test coverage; report tested SHA and test type |
| 5 Backup & Export | `feature/workmeter-backup` | Safe local/cloud backups and restore semantics, minimal status UX |

Additional active branch `feature/solo-shift-gps-drive-import-v24` must be reviewed for integration of workday GPS lifecycle and dated export/Drive import; do not assume it is already merged.

**Change control:** For cross-module changes, first define a shared contract in `docs/`; agree integration order; avoid parallel conflicting edits to `MainActivity.kt` and `GpsTrackingService.kt`; test a combined exact SHA before merge. Do not merge/rebase/cherry-pick unrelated branches without coordination.

**Definition of done:** behavior implemented, no data regression, Polish labels, theme consistency, widget adaptation where relevant, successful tests on exact HEAD, clear known limitations and (for Android-only functionality) an on-device validation plan.

## 14. Reference standards

Use current official guidance as applicable:
- Material Design 3: https://m3.material.io/
- Android design for mobile: https://developer.android.com/design/ui/mobile
- Android app widgets: https://developer.android.com/develop/ui/views/appwidgets
- WCAG 2.2: https://www.w3.org/TR/WCAG22/
- Android accessibility: https://developer.android.com/guide/topics/ui/accessibility

**Rule for all chats:** Read this document before starting a UX-relevant feature. If new functionality conflicts with it, explain the conflict and propose a documented spec change instead of silently creating an inconsistent design.
