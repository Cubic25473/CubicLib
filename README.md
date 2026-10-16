# CubicLib

An independent, Java-first FTC command framework with configurable dead-wheel odometry and a robot-hosted performance dashboard. **No Ivy or Pedro dependency.** Version 0.1.0 is an initial implementation, built against FTC SDK **12.0.0** and not yet tested on a physical robot.

## Included

- Commands, subsystem requirements, defaults, triggers, sequential/parallel/race/deadline groups and timeouts. Stop and exception paths cancel commands and invoke subsystem cleanup.
- goBILDA 4-Bar (32 mm / 2,000 counts), Swingarm (48 mm / 2,000 counts), and custom encoder/wheel/gearing configurations.
- Pinpoint, two tracking wheels + IMU, or three tracking wheels. Software localizers use mounting geometry and finite-turn integration.
- A dashboard bundled in the Android library, automatically started with the Robot Controller activity on port **8087**. No cloud, internet, laptop server, Ivy or Pedro required on the robot.
- TeleOp samples at up to 20 Hz, battery/loop measurements in exports, event markers, JSONL recording, replay and export/import. Browser updates poll at 4 Hz.
- BIOBUZZ 2026–2027 nominal field schematic, stationary-time review and event-based static route comparisons.

## Build

Use JDK 17 or newer compatible with Gradle 9.1 (this checkout was built using Android Studio's JDK 25), Android SDK platform 34, and internet access for Gradle dependencies:

```sh
./gradlew :cubic-core:test :cubic-ftc:assembleRelease publishAllPublicationsToDistributionRepository
npm test
```

Set `JAVA_HOME` to your JDK and `ANDROID_HOME` to your SDK, or configure `sdk.dir` in an untracked `local.properties`. The Gradle wrapper is included. No Node package installation is needed for dashboard tests.

The Maven repository is generated under `build/maven/`; Android AAR and Java JAR outputs are under their module `build/` directories. The AAR alone does **not** contain its transitive dependencies—use the Maven installation below.

## Public Maven repository

Add this to your FTC project's existing repositories block:

```groovy
maven { url = uri("https://cubic25473.github.io/CubicLib/maven/") }
```

Then add to TeamCode's dependencies:

```groovy
implementation 'dev.cubiclib:cubic-ftc:0.1.0'
```

Keep `google()` and `mavenCentral()` for transitive dependencies. Requires FTC SDK 12.0.0. Sync Gradle, then follow the robot setup steps below; the local Maven folder is unnecessary with the hosted repository.

## Install into an FTC Robot Controller project

1. Build CubicLib as above, or extract the release archive containing the `maven/` folder.
2. Add that Maven folder to the FTC project's repositories. For example, if the source checkout is a sibling directory:

   ```groovy
   // Root build.gradle, inside allprojects { repositories { ... } }
   maven { url = uri("${rootDir}/../CubicLib/build/maven") }
   ```

   For an extracted archive, use its `maven` directory instead. Retain `google()` and `mavenCentral()` for dependencies.
3. Add to `TeamCode/build.gradle`:

   ```groovy
   dependencies {
       implementation 'dev.cubiclib:cubic-ftc:0.1.0'
   }
   ```
4. Sync, build and deploy the Robot Controller app. This release requires the application's FTC SDK 12.0.0 dependencies. It does not bundle a second FTC SDK.
5. Connect your computer to the robot's network and open `http://<robot-ip>:8087/`. A Control Hub commonly uses `http://192.168.43.1:8087/`; use the actual IP shown by your setup if different. An external public website is not involved. The dashboard becomes unavailable when the Robot Controller app or robot is off.
6. Open **Robot setup**, select the backend/pod preset, and replace the example hardware names, mounting offsets, start pose, IMU orientation and robot dimensions. Save before selecting an OpMode. The setup JSON allows different pod scales for software localization; this Pinpoint adapter requires matching scales.
7. Copy `examples/CubicTeleOp.java` into TeamCode, configure its four drive motor names/directions, then Init and Start. A/B mark driver-confirmed pickup/scoring events. Stop finalizes the log; the website stays running. Choose a recording under Session library or export it.

The startup annotation is kept by the included consumer ProGuard rules. If using a custom Robot Controller that does not scan FTC `@OnCreate` annotations, call `CubicLib.install(context)` from its activity setup. `CubicOpMode.init()` also installs it as a fallback.

## Write robot code

Extend `CubicOpMode`, implement `configure()`, and optionally override `onStarted()`, `onLoop()` and `onStopped()`. `configure()` runs during Init and registers your subsystems and command bindings. Localization is updated once per loop, followed by the scheduler, your loop hook and sampling. `CubicOpMode` is intended for **TeleOp**; it does not infer official match periods or implement an autonomous path follower.

```java
scheduler.register(intake);
new Trigger(scheduler, () -> gamepad1.right_bumper).whileTrue(
    Commands.run(() -> intake.setPower(1), interrupted -> intake.stop(), intake)
);
scheduler.schedule(Commands.sequence(
    Commands.instant(() -> mark("intake-start")),
    Commands.timeout(collectCommand, 2.0),
    Commands.instant(() -> mark("score-attempt"))
));
```

`intake` and `collectCommand` above are your own mechanism/command implementations. Commands are reusable after completion; never schedule the same mutable child instance independently while a group owns it. Groups reject duplicate children and parallel subsystem conflicts. `whileTrue` schedules on the rising edge and cancels on release; it does not repeatedly restart a finished command. Scheduler mutation and subsystem access belong on the OpMode thread. A stopped scheduler is terminal; create a new scheduler for each OpMode.

Use `mark("pickup-confirmed")` and `mark("score-confirmed")` only on actual success. Attempts and command completion are not counted as scores. `resetPose(new Pose2(xMm, yMm, radians))` starts a new path segment; reports will not bridge that reset. All actuator subsystems must implement `stop()` so interruption/error cleanup can make your mechanisms safe.

For external localization, override `createLocalizer(RobotConfig)` with your `Localizer` adapter. Do not update an external pose source a second time in the adapter if another owner already updates it. Use the core `Scheduler` or `SessionRecorder` independently if integrating into an existing OpMode instead of the base class; wire their start/update/stop lifecycle yourself.

## Coordinates and calibration

Field origin is bottom-left as viewed on the dashboard: +X right, +Y up, heading counterclockwise from +X. Distances are millimeters; angles are radians. Robot-relative wheel x is forward and y is left; wheel angle is measured from forward toward left. `startPose.heading = 0` means the robot faces field +X.

For each custom pod, supply **decoded encoder counts per encoder revolution**, effective wheel diameter, encoder revolutions per wheel revolution, direction, mounting location and rolling angle. Do not confuse vendor PPR with decoded counts. Conversion is `mmPerCount = π × diameter / (counts × encoderRevsPerWheelRev)`.

Pinpoint uses wheel 0 as forward and wheel 1 as left. Its x offset is the forward wheel's sideways/y position; its y offset is the left wheel's forward/x position. Keep the robot stationary during Init for its IMU calibration. Two-wheel software localization requires the selected REV Hub IMU mounting orientation; three-wheel localization requires independent, non-singular wheel geometry. SDK encoder readings include motor direction; use dedicated encoder channels with stable direction settings.

Before relying on reports, push the robot a measured distance forward and left, then rotate it in place. Verify distance, signs, heading and negligible translation during pure rotation. Bad scale or offsets produce misleading reports. A non-finite IMU reading latches software localization invalid until `resetPose` is called with a known pose; movement during sensor failure cannot be reconstructed. Configure each robot, even when selecting a goBILDA preset.

## Performance reports and field limits

The BIOBUZZ view is an **original nominal schematic** derived from TU04 §9, not a CAD-accurate field certification. The center structure is modeled as a conservative no-drive envelope that includes the illustrated HIVE extent; flowers use conservative boxes. The map does not track moving robots, loose game pieces, hive tilt, robot mechanisms at different heights, or required scoring approach.

Route comparison stays disabled until the user confirms the field envelope and robot dimensions in setup. It compares recorded pickup-to-score cycles with a static 60 mm grid route, conservatively expanded by half the robot's diagonal plus clearance; every smoothed shortcut is collision-checked. It reports potential distance reduction, **not guaranteed time savings or a legal/available match route**. Code does not send suggested routes to robot motors. Field data and the collision envelope are in `cubic-ftc/src/main/assets/cubiclib/field.json`.

Pauses below 30 mm/s lasting at least 1.5 seconds are review opportunities. Distance ignores individual movements below 2 mm; noise/drift can still affect metrics. Gaps over 300 ms, missing sample sequences, invalid localization and pose resets break path calculations. Live diagnostics are odometry estimates, not ground truth. Current reports count confirmed cycles and show path/pause suggestions; they do not score matches or generate machine-learning coaching.

## Storage and network behavior

Logs are private Android app files under `files/cubiclib/sessions`. They are recorded without a browser attached, written on a background thread with a 2,048-record queue and flushed approximately once per second. Queue overflow is reported. Sessions have a 15-minute/18,000-sample limit and 5,000-event limit; the newest 20 sessions are retained. Export important sessions before retention removes old ones. App removal or clearing app data removes recordings. Power loss can lose the last unflushed records; JSONL import recovers complete lines and marks an unfinished recording incomplete.

All dashboard assets are local. Read-only telemetry is available to clients on the robot network. Setup writes require a same-origin token and are rejected while a CubicOpMode is initialized or running. No remote drive or OpMode-start endpoint exists. For ordinary OpModes that do not attach to CubicLib, the library cannot know their lifecycle; configuration changes only affect a future CubicLib Init and do not touch their hardware. Follow current event wireless rules; offline recording does not require keeping a browser connected during competition.

## Preview and verification

```sh
npm run preview
# Open http://127.0.0.1:8087 and choose Load demo.
```

This static development preview has no robot API, so “Robot disconnected” is expected. The demo is clearly labeled simulated. It exercises field replay and report interactions. Robot setup, live streaming, and stored robot sessions require the deployed Android library.

Automated coverage includes scheduler cancellation/errors/groups, pose math, encoder rollover, custom pod scale, JSONL persistence/import, gaps and collision-checked route candidates. Physical Control Hub timing, encoder calibration, SDK lifecycle discovery, competition networking and drive hardware must still be verified on the actual robot. See `docs/VALIDATION.md` for the verification record and hardware acceptance steps.

## References

- [FTC SDK](https://github.com/FIRST-Tech-Challenge/FtcRobotController)
- [BIOBUZZ official season resources](https://ftc-resources.firstinspires.org/ftc/archive/2027/game)
- [goBILDA 4-Bar](https://www.gobilda.com/4-bar-odometry-pod-32mm-wheel/) and [Swingarm](https://www.gobilda.com/swingarm-odometry-pod-48mm-wheel/)

CubicLib is independent of FIRST, goBILDA and Pedro Pathing. Their names identify compatible hardware and competition resources; no affiliation is implied.

## Bézier paths and mecanum heading

`dev.cubiclib.path.BezierPath` supports lines (2 control points), quadratic curves (3), and cubic curves (4). Control points use `Pose2` x/y in mm; their headings do not define the curve. Robot orientation is specified separately:

```java
BezierPath strafeCurve = BezierPath.constantHeading(0,
    new Pose2(400, 400, 0),
    new Pose2(800, 1200, 0),
    new Pose2(1600, 1200, 0));
Pose2 target = strafeCurve.at(0.5); // Heading stays 0 while position follows the curve.

BezierPath turningCurve = BezierPath.interpolateHeading(
    Math.toRadians(170), Math.toRadians(-170),
    new Pose2(400, 400, 0), new Pose2(800, 1200, 0),
    new Pose2(1200, 1200, 0), new Pose2(1600, 400, 0));
```

Heading interpolation takes the shortest angular arc. `t` ranges from 0 to 1 and is a curve parameter, not elapsed time or normalized distance. `sample(100)` returns poses; `approximateLength(100)` estimates distance in mm. `tangentHeading(t)` is the direction of travel and is deliberately separate from robot heading. These are geometry utilities, not an autonomous motor controller.

The report rounds candidate-route corners using quadratic Bézier segments. Recursive control-point bounds verify clearance over the entire curve, not just discrete samples. Unsafe corners retain their checked line segments. Candidate distance uses a 64-step approximation per curve. The robot footprint remains conservatively bounded by a circle; candidate curves do not prescribe a heading trajectory. Recorded heading always comes from localization and is preserved during strafing and replay.

Robot setup now exposes **Start pose X (mm), Y (mm), Heading (degrees)** alongside the JSON configuration. Bottom-left is the field origin; 0° faces right and 90° faces up. Save while stopped; the pose initializes localization on the next Init. The fields and JSON stay synchronized. Configuration does not reset the robot during an active run.
