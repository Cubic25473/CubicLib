package dev.cubiclib.command;
/** All callbacks execute on the OpMode thread. stop() must make actuators safe. */
public interface Subsystem {
    /** Run each loop, automatically require this subsystem and stop it on cancellation. */
    default Command run(Runnable action) { return Commands.run(action, interrupted -> stop(), this); }
    /** Perform one action with exclusive ownership of this subsystem. */
    default Command once(Runnable action) { return Commands.instant(action, this); }
    default void periodic() {}
    default void stop() {}
}
