# WorkMeter SOLO UX package v2 — implementation continuation

Source package: `docs/design/workmeter-solo-implementation-package-v2.md`, supplied by the owner.
Base for this increment: `15857a5d1e24551d1bfdcdeacc84667c3e061361` on
`feature/solo-2-ux-20261105`. Remote refs were fetched again before editing;
phone-review remains `32c19a4`, main remains `8ba4070`. No branches were merged.

## Implementation map

| Requirement | Result in this increment or preserved baseline |
| --- | --- |
| UX-01 Start | Text and semantic stage indicator, one primary action, secondary day close, quick actions; no disabled GPS action on a closed day. |
| UX-02 Tak/Nie | Existing shared Home/widget lifecycle and financial PIN retained; regression suite remains enabled. |
| UX-03 Search | Shared fields and accent/token matching, relevance then actual recorded use then stable TAG/ID; two-line picker identity/address; archived filtering and session validation retained. |
| UX-04 GPS | Existing capture freshness/accuracy/ID/session guards retained; actionable capture errors, retry and manual object fallback; arrival proposal uses Inny obiekt / Tak, tutaj. |
| UX-05 Obiekty | List uses all search fields and recorded recency; legacy object card now uses all three palettes; grouping/archive/settings routes preserved. |
| UX-06 Mój czas | Day cards, compact interval rows and accessible overflow; stage cards show short same-day intervals and expandable route; deletion states date/consequences and existing undo limit. |
| UX-07 Portfel | Existing validated due value first, labelled period, early payment/expense actions; whole-history and reimbursement figures remain separate; ambiguity warnings retained. |
| UX-08 Photos/queue | Existing durable local save and explicit enqueue preserved; Dodaj zdjęcie and one queue action; shared record cards wrap long status badges. |
| UX-09 Materials | Existing multi-line draft, catalogue, unit validation, car stock and row editing retained; shared card typography and local-save status applied. |
| UX-10 Notes | Cards with snippet/object/status, direct completion through the existing persistence path, existing history and offline access retained. |
| UX-11 Charts | Explicit period/paid-work total and separate personal time above existing TimeAnalytics charts, legends/details/axis logic retained. |
| UX-12 Reports | Explicit Wednesday–Tuesday shortcut, manual SMS dates/copy preserved; read-only CSV/PDF controls and dated filenames exposed in existing backup/export screen. File exports explicitly cover full saved history in that screen. |
| UX-13 Settings | Six collapsible categories with current theme/GPS/PIN/version labels; section settings reuse existing context gear destinations. |
| Widget | Same three render classes support 2×1/2×2/4×2/4×3 by dp bounds. Large layout has a distinct stage chronometer and actual stage distribution instead of a duplicated daily counter. Critical 48dp controls and PendingIntent paths retained. |

Shared card/button spacing, semantic theme colors and adaptive text wrapping apply across
photos, materials, notes, wallet and history. Transient per-screen scroll positions are
restored on navigation. Existing search/filter/process-state handling remains in place.

## Targeted report integration

Reviewed report controls/selection changes from `a251143` and lossless PDF wrapping
from `3f742cc` in `feature/workmeter-backup`. Ported the relevant report/PDF source
and tests, with only the export action added to the current controller.
The separate restore-concurrency commit `52a8513` is excluded.
Reports label raw workday intervals as **brutto**; they do not claim payroll totals.
Stage/object/date selection fixes are read-only and do not rewrite authoritative records.

## Data boundaries

No changes to application ID, signing, stored theme keys, object IDs, schema, wallet
calculation rules, GPS sensor collection, upload batching/idempotence, material validation
or restore storage. Note completion delegates to the previous detail-screen mutation.
The app remains version 2.7.0/code 27 for this test branch.

## Validation

New JVM coverage: relevance/recency/address fields, archived eligibility, Wednesday–Tuesday
boundaries/year rollover. Imported report selection and PDF wrapping regressions.
New Android coverage: multiline picker and stable ID, settings/PIN/data preservation,
distinct widget stage clock; existing widget matrix now includes 200% font scale.
Imported real CSV/PDF output and controller cancellation/busy/duplicate-callback tests.

`git diff --check` passed before commit. Full CI results must be read against the published
final SHA; this preparation document does not pre-assert test/build success.

Remaining physical-device acceptance: Samsung One UI and other real launchers, resizing,
rotation/two instances/per-instance theme, wallpaper contrast, actual GPS/permissions,
camera/EXIF, Drive/offline retry, reboot/Doze, and installation over the prior signed APK
with retained real records. Full-screen visual acceptance at all phone sizes and 200%
text scaling is separate from the widget bounds matrix.
