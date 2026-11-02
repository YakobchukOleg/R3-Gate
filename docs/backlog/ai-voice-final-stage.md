# AI and voice input — late-stage roadmap

Status: **deferred intentionally** (penultimate product milestone, after full manual testing).

## Product decision — 2026-11-02

- Implement the **entire planned application functionality first**.
- The product owner must then **personally test the completed non-AI functionality on a real device** and validate workflows, navigation, recorded work time, GPS behavior, photos, materials, objects and reports.
- Introduce **artificial intelligence and speech recognition / voice input only after this personal validation**. Schedule this as the **penultimate milestone** rather than implementing it now.
- Preserve a clean foundation from the beginning: stable typed domain models and event interfaces, isolated service boundaries, opt-in permissions/consent, replaceable AI and speech adapters, well-defined manual fallback, and tests without external AI services.
- **Do not activate AI-driven automation, speech recognition, microphone capture, or transmission of personal data during current development.**
- Any future AI suggestions must remain reviewable by the user and must not silently overwrite manual work records, GPS trip categories or payroll calculations.
- Privacy decisions for sharing GPS-derived work summaries with a manager remain **open for later discussion**; do not implement raw location sharing by default.

## Proposed release sequence

1. Complete every core non-AI feature, including GPS, objects, materials, photos, time tracking, categories, reports and reliability.
2. Conduct personal end-to-end testing and fix identified issues.
3. **Penultimate:** add AI and speech-to-text/voice input with explicit opt-in and user-controlled confirmation.
4. Final stabilization, privacy/security checks and release readiness.

This is a roadmap note, not permission to implement AI functionality before the above gates.
