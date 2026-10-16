# Verification record — 0.1.0

Validated October 10, 2026 on macOS using Android Studio JDK 25, Android SDK 34, Gradle 9.1 and FTC SDK 12.0.0 artifacts.

- Java core tests: command lifecycle, conflicts, default commands, deferred mutation, exception cleanup, trigger edges, group cleanup, encoder overflow, heading wrap, two/three-wheel geometry, invalid data, finite-turn integration and background recording.
- JavaScript tests: static route clearance, blocked endpoints, gap/reset suppression, pause semantics, confirmed events, geometry gating and corrupt/incomplete JSONL input.
- Android release library compiles against the real FTC SDK dependencies. Packaging includes local HTML/CSS/JS/field assets and startup annotation keep rules.
- Browser demo: nominal BIOBUZZ view, 3.71 m sample run, 24 s replay, 3 s pause, one confirmed cycle and 1.20 m shorter static candidate. Simulated data is visibly labeled.

## Required on-robot validation

1. Build a Robot Controller application with the supplied Maven repository and confirm the dashboard opens on the robot network while no OpMode is selected.
2. Configure actual pod positions, scales, port names, robot dimensions and IMU orientation. Confirm the chosen preset matches the exact pod revision.
3. Test Init/Start/Stop repeatedly and confirm no duplicated server or recorder. Verify motors stop on Stop, command cancellation and a deliberately thrown command exception with wheels raised.
4. Compare pose against measured straight, sideways and rotation-only movements. Test both goBILDA presets and at least one custom scale. Do not enable field geometry confirmation until the envelope matches the team's field assumptions.
5. Run TeleOp with browser closed, reopen and replay/export. Interrupt app power to check incomplete-session recovery. Verify data-gap/invalid indicators with an intentionally disconnected sensor.
6. Measure OpMode loop timing, CPU, memory and disk throughput on the actual Control Hub under load, with and without a connected browser. The desktop build does not establish a real-time performance guarantee.
7. Verify local network availability and applicable event wireless rules independently before competition use.

Known limits: nominal field geometry; no dynamic obstacle sensing; conservative circular robot clearance; no task/heading-constrained route optimization; no physical-hardware validation in this environment.
