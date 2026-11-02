# Bluetooth-assisted GPS departure — design and implementation gates

Status: **decision rules + unit tests added; Android Bluetooth integration NOT yet enabled**.

## Intended user flow
1. User explicitly enables Bluetooth-assisted detection and chooses a paired **car** device (not arbitrary headphones or watches).
2. On connection, wait about 20 seconds before requesting GPS verification. Debounce duplicate connections; limit prompts to once per 10 minutes.
3. Bluetooth alone never establishes travel. Verify departure using accurate GPS observations, existing geozone and dwell rules.
4. If the user has truly departed from a known worksite, show a **suggestion** to switch into travel mode; never silently overwrite confirmed time, object, trip, pay or manually selected categories.
5. Disconnection cancels pending checks. Verification times out after three minutes. All identifiers, events and GPS verification remain on device.
6. GPS availability, Android 12+ Bluetooth runtime permission and battery state must be explained clearly.

## Android integration still required
- Request BLUETOOTH_CONNECT (Android 12+) when opt-in enabled; support older permission semantics.
- Surface paired-device selection and a clear on/off toggle in GPS settings.
- Implement connected/disconnected receiver and reliable lifecycle handling; classify only previously approved devices.
- Android 12+/14+ restrict starting location foreground services from the background. **Do not silently start GPS from an arbitrary Bluetooth broadcast.** If GPS service is not already running and background-location rules do not permit it, issue an actionable user notification to open the app and authorize verification.
- Integrate verification with arrival/departure evidence, power sampling and Android device testing. Bluetooth listening itself has energy cost; verify whether battery use actually improves.

This must be shipped only after permissions, foreground/background behavior and user consent are tested on real Android devices.
