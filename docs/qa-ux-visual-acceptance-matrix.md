# WorkMeter SOLO — UX and visual QA acceptance matrix

Source of authority: `main/docs/design/workmeter-solo-design-system.md` v1.0 (2026-11-03). This is a **test plan**, not evidence that target UI is implemented or verified.

## Evidence rules
For every assertion record **branch / exact SHA / Android API / device-or-emulator / launcher / theme / font scale / screenshot-or-test reference / result**. Never label a missing theme, stage, widget or unexecuted check as a PASS. Report `NOT_IMPLEMENTED`, `NOT_RUN`, `PASS`, `FAIL` or `BLOCKED` explicitly. JVM, lint, emulator and physical device evidence are separate. No merges without owner authorization.

## Three-theme screen matrix
For each of `Slate & Sage`, `Nordic Blue`, `Graphite Dark`, verify all of `Start`, `Obiekty`, object details, `Mój czas`, `Finanse` (PIN locked/unlocked), `Więcej`, `Ustawienia`, photo capture/link, materials, notes, Backup & Export. Repeat at normal and enlarged Android text scales, narrow screen and landscape where supported.

For each permutation check: semantic foreground/background contrast against WCAG 2.2, non-color-only state indication, no clipped Polish labels/buttons, practical 48×48dp targets, focus/screen-reader naming, insets/back navigation, theme persistence across activity recreation, unchanged active shift, stable object ID and data.

## Contextual Home state machine
| State | Primary action expectation | Critical prohibition |
|---|---|---|
| Day closed | Rozpocznij dzień | Do not show active shift/pay |
| Obiekt | Rozpocznij przejazd (if applicable) | No implicit GPS override |
| Przejazd | Potwierdź przyjazd only with credible evidence; otherwise Wybierz cel | No fabricated arrival |
| Zakupy | Zakończ zakupy | No unconfirmed billable interval |
| Prywatnie | Wznów pracę | No work GPS proposals without permission |
| Unspecified | Wybierz etap | No arbitrary object assignment |

Check one visually dominant action plus accessible manual correction, end day, quick photo and explicit GPS state. Test allowed/forbidden transitions against Core contract; do not infer legality solely from button visibility.

## Widget device matrix
Use launcher-provided actual min/max dp bounds, `AppWidgetManager` widget ID, orientation and `RemoteViews` layout. Grid counts alone are **insufficient**.

| Launcher target | Information priority | Actions |
|---|---|---|
| 2×1 mini | Stage/timer, object if space | Tap opens app |
| 2×2 compact | Time + stage + object | Primary if fits |
| 4×2 standard | Time/stage/object | Primary, camera, shopping if fits |
| 4×3 expanded | Standard + GPS/pending count/distribution | Valid context actions |

For each actual size: all 3 themes, app-theme inheritance and independent widget theme, background transparency 0%/50%/100% (foreground remains opaque), readable contrast, no clipping, functional PendingIntents, resizes, boot recovery, separate settings for 2 simultaneous widget IDs, offline and GPS-disabled fallback. Distinguish launcher/widget platform restrictions from app defects.

## One-tap audit (count user actions from visible Home/widget)
| Scenario | Target | Measurement |
|---|---|---|
| Accept safe GPS suggestion | 1 | Number of taps before proposal is accepted, not merely opened |
| Open camera | 1 | Number of taps to launch camera, excluding shutter/system permission |
| Open current confirmed object | Minimum practical | Count taps and repeated object selection |
| Manual stage correction | Short direct path | Count menus/dialogs and record unnecessary nesting |
| Close day | Short review/confirmation | Record destructive confirmation separately |

Track actual counts, device and screenshot evidence. Design objectives are not current product claims.

## Cross-module and stability invariants
- Confirm/later/reject GPS suggestion; same eventId across restart/restore; no duplicates or repeated rejected prompts
- GPS outage, provider permissions, day close, clock changes and Doze; no silent mutation of confirmed work/financial records
- Offline start/end stage, notes/photos/materials persistence; app restart/force-stop; no duplicate shifts
- Restore import preflight, damaged archive rejection, object ID preservation, explicit backup vs upload states
- App widget multiple IDs and persisted theme/opacity, boot refresh, camera and PIN routing
- Nonregression of existing P1–P4 screens and legacy preferences `r3_gate`

## Integration gates / ownership
UI + themes/widgets: Chat 3; GPS suggestion lifecycle: Chat 1; authoritative transitions and finances: Chat 2; Backup: Chat 5; independent evidence: Chat 4. Report defects via issues to owning chat; QA does not silently modify their modules.

**Observed contract discrepancy:** design spec §13 names `feature/gps-watchdog-motion-geofence` for GPS, while earlier coordination Issue #5 assigned `feature/prototype-p1-p4-native-menu`. Confirm current branch ownership before cross-branch GPS CI claims; do not change either branch without permission.

**Release gate:** No full-project success until a specifically approved integrated SHA has passed combined JVM/lint, instrumented Android, cross-theme/widget device checks and manual data/finance verification.
