package dev.cubiclib.command;
/** All callbacks execute on the OpMode thread. stop() must make actuators safe. */
public interface Subsystem {
    default void periodic() {}
    default void stop() {}
}
