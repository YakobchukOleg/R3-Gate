# Future idea: adaptive activity recognition using motion sensors and GPS

Status: product idea / backlog. Not implemented.

WorkMeter SOLO should gradually learn an individual user's *recurring, consented* patterns, with the goal of inferring work activity transitions (on site, travel, stationary, shopping, etc.) while reducing battery-intensive continuous GPS sampling.

## Concept
- Observe low-power on-device sensors, such as the accelerometer, motion/activity signals and changes in phone orientation/position, as preliminary cues.
- Learn repeated combinations of signals and known routines over time (e.g., phone placed in a pocket, no interaction, enters a car, then GPS confirms spatial movement).
- Use probable activity changes to trigger a short GPS verification window, rather than keeping high-frequency GPS running permanently.
- Combine location changes, motion history, regular locations and previously confirmed activity labels to improve accuracy.
- Classify travel and other activities, but distinguish tentative inference from confirmed evidence and allow user corrections.

## Engineering guardrails / discovery work
- Device orientation and lack of handset movement alone cannot prove car travel. Require corroborating signals, confidence thresholds, and debounce/hysteresis.
- Evaluate Android Activity Recognition, significant motion sensors, motion sensors in batching mode, and opportunistic location fixes to minimize wakeups and power consumption.
- Preserve an explicit user-controlled disable switch; use opt-in and explain sensor observation and personalized learning.
- Prefer local-only processing and bounded retention, with a way to clear learned behavior; no hidden profiling or server transmission.
- Manual timesheets and pay must not be overwritten from ambiguous automatically inferred behavior.
- Benchmark energy consumption and reliability in actual pocket, vehicle, walking, shopping, stationary and locked-screen scenarios; compare to a GPS-only baseline.
- Add unit tests and field validation before promoting automatic entries to authoritative records.

Original concept: long-term user habits + phone motion/orientation + sparse GPS confirmations can provide smarter, more battery-efficient activity detection.
