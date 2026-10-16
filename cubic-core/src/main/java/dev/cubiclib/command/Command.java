package dev.cubiclib.command;
import java.util.Collections;
import java.util.Set;
public interface Command {
    /** Interrupt this command after the given duration in seconds. */
    default Command withTimeout(double seconds) { return Commands.timeout(this, seconds); }
    /** Run another command after this one finishes normally. */
    default Command then(Command next) { return Commands.sequence(this, next); }
    default void initialize() {}
    void execute();
    default boolean isFinished() { return false; }
    default void end(boolean interrupted) {}
    default Set<Subsystem> requirements() { return Collections.emptySet(); }
    default String name() { return getClass().getSimpleName(); }
}
