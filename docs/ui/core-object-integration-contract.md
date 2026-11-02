# UI ↔ Core integration contract — WorkMeter SOLO

Owner: Chat 3 (UI), branch `feature/workmeter-ui`. Coordination: [Issue #8](https://github.com/YakobchukOleg/R3-Gate/issues/8).

## Existing entry points (observed on 2026-11-02)

`MainActivity.kt` owns UI and mutations together:
- `objects()` (around line 1734): active/archive listing toggled using `archived_objects`.
- `objectDetails(id)` (around line 1747): read-only historical work totals, notes/materials/photos links, archive/restore confirmation at lines 1780–1785.
- `objectPicker(optional, arrival, selected)` (around line 1664): excludes archived objects from new selection.
- `newForm("objects", id)` + `saveForm()` (around line 2049): object creation/editing and validation.
- `objectById(id)` (around line 1623): resolves archived and active objects for history.
- `recordList`, `newForm`, `saveMaterials`: associated records need Core-side validation at write time.

These private methods are **not** stable cross-module APIs. UI must not directly mutate JSON or duplicate Core validation.

## Proposed minimal Core → UI contract (request to Chat 2, not yet implemented)

Provide a UI-independent `ObjectUiGateway` (name provisional) with:
1. `listObjects(includeArchived: Boolean): List<ObjectSummary>` where `ObjectSummary` contains stable `id`, display fields, and `archived`.
2. `getObject(id: String): ObjectDetails?` INCLUDING archived records, to preserve historical views.
3. `validateObjectDraft(draft): ValidationResult` and `saveObject(draft): Result<id>` including city/address/tag constraints.
4. `setArchived(id: String, archived: Boolean): Result` preserving IDs and historical links; UI supplies confirmation only.
5. `selectableObjects(): List<ObjectSummary>` returning active records only.
6. `validateAssociation(objectId: String?, recordKind: Kind, operation: CreateOrEdit): ValidationResult`: prevent **new** links to archived objects but allow viewing historical records and safely editing old records without silent data loss.

Interface requirements: clear error codes/messages (translated to Polish by UI), no Android Activity/View dependencies, no database schema changes solely for UI, stable IDs, no side effects on read.

## Ownership and conflict boundary

Chat 2 owns gateway implementation, object validation, persistence, archive semantics and association rules; Chat 3 owns lists, archive badges, picker, form layout, Polish messaging, confirmations and screen navigation. Chat 4 owns cross-feature regression and instrumentation tests. Chat 1 GPS remains untouched.

`MainActivity.kt` is a high-conflict file (GPS and UI mixed). Chat 3 will introduce standalone View/helper files first, leaving integration hooks unconnected until the Chat 2 API is agreed. Do not directly modify shared `MainActivity.kt` simultaneously in both branches. Coordinate any small integration diff through Issue #8 before implementation.

## Acceptance checks

- Active objects are selectable for new records; archived objects are not.
- Archived objects remain visible through archive/history and old linked records stay readable.
- Archive/restore requires confirmation and preserves existing IDs and data.
- New object creation displays Core validation errors without duplicating their business rules.
- Existing navigation, GPS behavior, user data and Polish UI are preserved.
- CI and device UI tests run at integration time; merely adding this contract is not a passing CI test.

Status: **proposal only**. No Core API or behavior is claimed implemented by this document.
