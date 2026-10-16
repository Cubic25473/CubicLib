package dev.cubiclib.command;
import java.util.Collections;
import java.util.Set;
public interface Command {
    default void initialize() {}
    void execute();
    default boolean isFinished() { return false; }
    default void end(boolean interrupted) {}
    default Set<Subsystem> requirements() { return Collections.emptySet(); }
    default String name() { return getClass().getSimpleName(); }
}
