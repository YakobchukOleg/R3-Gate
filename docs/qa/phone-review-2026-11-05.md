# Phone feedback integration — 2026-11-05

Branch: `fix/phone-review-20261105`. `main` is unchanged.

## Sources and compatibility

Base: installed/tested 2.3.3, `fd43e8743ea2a89af1f33496a967915575b59a25`.
GPS `2155ae1`, Core `41bbcb8`, UI `b671e90` are ancestors of that base.
Integrated Backup `8cd96e6a31f410d9985b678e3509727d9c564347` and widget corrections `c87d4b83d173885a9396ff291bcaf12cd0ec13d6` with preserved merge parents.
Application ID, existing `r3_gate` data, object IDs, originals, rates and financial PIN are retained. Version 2.7.0/code 27 uses the existing stable signing secret.

The user's phone-review assignment explicitly supersedes the older design document in three respects: workday Yes/No confirmation instead of PIN, explicit photo sending instead of auto-queue, three widget classes and at least 50% opaque background. The shared specification itself is not rewritten.

## Implemented areas

1. Universal token search across name, organization, city, tag and address; Polish accent folding, partial any-order AND matching; active ordered results on empty query; existing selection handlers reused.
2. Start/stop Yes/No shared by Home/widget routes. Start persists an unassigned active day after confirmation, then launches authorized GPS and object lookup. Cancelling the subsequent picker keeps that explicitly opened day running; cancelling the initial confirmation changes nothing.
3. Independent bounded one-shot GPS association, quality checks, stable object ID, replacement confirmation and session revalidation. Travel uses fresh known-object lookup and manual fallback through existing Core validation.
4. Compact (<110dp tall, <250dp wide), medium (<110dp tall, >=250dp wide), large (>=110dp tall) RemoteViews. Primary start/stop always present. Large adds camera and available stage actions. Narrow secondary captions shorten; very short large boxes necessarily sacrifice secondary information before critical actions. Tall layouts distribute timer space.
5. Per-instance themes, background-only alpha floor, opaque text backing; shared renderer for actual dp preview presets and installed widget bounds. Stage labels replace ONLINE; no unverified cloud-success icon. Optional real daily total uses the existing analytics engine.
6. Local photo saving, one send/queue entry, durable explicit photo selection so an older queued job cannot include subsequently added photos, repeated pending-batch taps deduplicated, distinct explicit batches appended sequentially, and existing Drive idempotence preserved. Queue generations prevent an older completed batch from clearing a newer pending batch. Queue shows local/pending/sent/error counts and connectivity.
7. Chronological day-stage cards including gaps, travel endpoints and non-billable personal time. Read-only presentation; source intervals unchanged.
8. Wallet/object header gears and dedicated settings, existing financial PIN and list controls retained.
9. Chart legend, interactive hourly details and accessible hourly text table; multi-day sums and existing dynamic maximum retained. Charts/widget use the same read-only analytics adapter.
10. Current Backup settings controller connected to existing document picker/Drive authorization and three theme palettes; safe restore preview/confirmation remains owned by Backup. A confirmed restore locks financial access and stops the previous GPS session so restored data cannot inherit its sensor session.

## Validation

Local focused JVM runs: 48 distinct search/layout/opacity, GPS/photo-selection/time analytics and Home-state tests passed. These are not device tests.
CI runs all Gradle unit tests, Android lint, signed compilation and complete Android 15 instrumentation. The new `PhoneReviewFlowTest` verifies confirmation/cancellation, widget routing, GPS capture eligibility, and layout bounds across five actual sizes, six states, three themes and larger fonts. Existing regressions remain enabled with updated approved UI expectations.
Only a completed successful CI on the final SHA establishes automated acceptance. See the corresponding GitHub Actions run, not intermediate cancelled/failed runs.

## Real phone checks still required

Samsung One UI widget resizing/reconfiguration, several instances, font/display scale, real GPS and permission revocation, actual Drive upload/account authorization, camera/EXIF, offline retry, long-running battery/Doze/reboot and installing over the existing signed app while retaining its real data. No physical-phone success is claimed by JVM/emulator tests.
