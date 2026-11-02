# Colota research for WorkMeter SOLO — first technical review (2026-11-02)

Verified sources:
- https://github.com/dietrichmax/colota
- https://colota.app/docs/introduction/
- https://colota.app/docs/guides/tracking-profiles/
- https://colota.app/docs/guides/geofencing/
- https://colota.app/docs/getting-started/installation/

## Verified architecture and behavior
- Open-source Android app under **AGPL-3.0**: React Native UI + native Kotlin Android tracking modules. Native location foreground service, local history, geofences and optional sync.
- Its tracking profiles adjust GPS interval, distance filter and sync policy based on charging, Android Auto, **selected Bluetooth device**, Wi-Fi, speed and stationary state. Supports priority and activation/deactivation delays.
- Stationary conditions use GPS speed and accelerometer/significant-motion cues. Device motion detection is a hint, not definitive vehicle travel.
- Geofence pause zones can suspend recording and (when motionless or connected to Wi-Fi) stop GPS entirely; sensing/motion can resume the stream. It preserves a delayed exit fallback and may request periodic positions while paused.
- Can work offline and without Google Play Services (FOSS build), but does **not bypass Android's background restrictions**. Foreground tracking, permissions and power constraints still apply.
- Its author documents location anchor points at zone exits. Such synthetic positions must **never** be treated as original measured fixes or proof for time/pay decisions in WorkMeter.
- Colota's primary purpose is location history and optional backend upload, not the user's confirmed timesheet. Never transplant tracking or uploading defaults into WorkMeter.

## Practical patterns worth adapting **after completing and testing our GPS**
1. A **stationary low-power profile** with clear wake-up conditions and verified fallback.
2. Profile activation/deactivation hysteresis to avoid flicker at traffic lights and site boundaries.
3. A low-cost geofence pause policy with cautious proof-of-presence semantics.
4. Clear on-device diagnostics for interval used, why tracking is paused, and last real fix.
5. Optional future car Bluetooth integration, **off by default**, separately planned in bluetooth-assisted-gps-departure.md.

## Current WorkMeter status and gates
- GPS already has opt-in tracking, adaptive 30 s / 90 s sampling, geozones, journey hints, manual confirmation and diagnostic counters.
- Newly added GpsGeofenceEvidence differentiates ambiguous boundary fixes from clear departures; protects against accidental false trip suggestions.
- GPS cannot be called fully completed until final CI succeeds and the owner completes on-device background, reboot, no-signal, boundary, charging and battery tests.
- Do not activate Bluetooth integration, AI, speech recognition or external GPS uploads at this stage.
- Evaluate battery savings empirically; fewer GPS fixes are not automatically equivalent to lower consumption across all phones.

## Licensing
AGPL-3.0 may introduce reciprocal source-disclosure obligations if copying Colota code into a distributed app. **Study patterns and independently implement compatible concepts** unless license obligations are assessed and accepted.
