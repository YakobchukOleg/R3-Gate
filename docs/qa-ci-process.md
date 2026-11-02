# WorkMeter SOLO — QA checks (Chat 4)

Branch ownership: `feature/workmeter-qa`. This workflow is intentionally separate from the shared `.github/workflows/android.yml`.

## Fast QA path

`.github/workflows/qa-checks.yml` runs on pushes and PRs **to `feature/workmeter-qa`**, or by manual workflow dispatch once GitHub exposes it. It runs `testDebugUnitTest lintDebug`, **not** `assembleDebug`, emulator tests, signing or APK upload. See GitHub Actions / `WorkMeter QA (unit tests and lint)` for the run and its reports.

### Cross-branch limitations

GitHub evaluates workflow triggers using the workflow files present on the branch of the event. A workflow committed only to the QA branch **does not automatically test** commits on `feature/workmeter-core` or any other sibling branch. The normal Android workflow currently triggers on pushes to `main` and PRs targeting `main`, so standalone feature pushes do not automatically run it.

The Core change `1847057ca5ca7974d484c99e74fb099aee69796b` must be checked **at that exact commit** (or through a separately approved test integration) before reporting it passed. A green QA-branch run is **not** a test of Core.

Recommended coordination: each branch owner can independently add a compatible fast-check workflow in its own branch after agreeing on the contract, or open an approved PR using existing checks. Do not merge, rebase, cherry-pick or change sibling branches for testing. A centralized cross-branch dispatch mechanism requires separate review and permission before it can safely check out arbitrary branch refs.

## Android instrumentation regressions

`LocalDataPersistenceRegressionTest.kt` provides device-only tests for persistence and duplicate prevention after Activity recreation. They are **not exercised** by the fast unit/lint job. Run `connectedDebugAndroidTest` in an explicitly approved emulator workflow or on a configured local emulator. Report JVM CI and emulator results separately; never infer emulator success from unit/lint success.

## Run status checklist

1. Verify the intended branch HEAD and commit SHA.
2. Open the Actions run, confirm its exact `head_sha` and completed conclusion.
3. Check `testDebugUnitTest` and `lintDebug` steps and archived reports.
4. In Issue #9 report: changed files, test count, tested SHA, actual result, run URL, limitations and next task.

The fast workflow may still download and compile dependencies and resources; its omission of APK assembly does not mean compilation is skipped.
