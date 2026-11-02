# WorkMeter SOLO — Central QA registry

Coordinator: Chat 4. Tracking branch: `feature/workmeter-qa`.
This is a snapshot, not an integration or an assertion that untested branches are green.

| Chat | Module | Branch | Observed HEAD | CI evidence | Interpretation |
|---|---|---|---|---|---|
| 1 | GPS | `feature/prototype-p1-p4-native-menu` | `1d51fe8489951a8bf829265a391a6ed5770c53e5` | [41519986846](https://github.com/YakobchukOleg/R3-Gate/actions/runs/41519986846), pending at observation; SHA `1e11ea5` | No confirmed pass for current HEAD |
| 2 | Core | `feature/workmeter-core` | `78387236a9ff5696e886ed2b040466dee05c9cbf` | No run returned by branch query | Unverified |
| 3 | UI | `feature/workmeter-ui` | `f81bb24322bb6a8e33c2a8414bdcfe674f3eece2` | No run returned by branch query | Unverified |
| 4 | QA | `feature/workmeter-qa` | `ffe5476584dc41514069149f2732f845895af1fc` | [41517586773](https://github.com/YakobchukOleg/R3-Gate/actions/runs/41517586773), completed success | Fast JVM test + lint pass; instrumentation tests not run |
| 5 | Backup/Export | `feature/workmeter-backup` | `9d6a79ea202c6a3edec009d07972c6fc5f797f09` | No run returned by branch query | Unverified |

## Shared acceptance gates

1. Branch owner provides exact HEAD SHA, module, new test count, test command, link to Actions run, known defects and dependencies in its GitHub Issue.
2. Coordinator compares the run's `head_sha` with reported SHA and verifies terminal `success`. Pending, cancelled, absent, or older-sha checks do not pass.
3. Fast checks: JVM unit tests and lint on the branch. Device checks: Android lifecycle, persistence, permissions, restart and offline flows via emulator, separately labelled.
4. Cross-module scenarios: GPS events must not alter confirmed shifts/payment; object IDs remain stable across Core, UI and Backup; work sessions and linked materials/photos persist through restart; exports round-trip without losing records.
5. A green check in one branch never means an integrated application is verified. Integration testing awaits explicit owner approval of a combined code state. No merge, rebase or cherry-pick by QA.

## Follow-up

Request independent CI evidence for the current HEAD of Core, UI and Backup through their owning chats, without editing their branches. Re-check GPS when its current HEAD has a completed run. Review QA device tests separately; the fast workflow does not execute `connectedDebugAndroidTest`.
