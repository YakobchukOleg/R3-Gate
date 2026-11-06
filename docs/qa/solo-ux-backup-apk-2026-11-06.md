# WorkMeter SOLO 2.7.1 — UX v2 and current main

Integration candidate for the requested phone APK. Base main: 2d2265c6; UX source: c77864b (PR 20). Main and the source PR are not modified by this candidate.

The candidate preserves UX v2 layouts, adaptive widgets and the prior phone fixes while retaining current main's verified restore conflict handling, shared Backup Activity, restore-to-Core migration result and readable report selectors. The UX settings Data category routes to the single Backup Activity rather than keeping a second inline host.

Version: 2.7.1 / code 28. Application ID, preferences and stable signing identity are unchanged. Full JVM, lint, Android 15 instrumentation and signed APK verification run on the integration PR. Packaging records exact tested SHA, checksum, actual manifest package/version and certificate digest.

Physical-phone acceptance still includes install-over with data retained, launcher widget resize, real GPS/camera and real Google Drive. Emulator tests do not replace those checks.
