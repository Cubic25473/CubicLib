package dev.cubiclib.command;
import java.util.function.BooleanSupplier;
/** Edge-triggered: whileTrue cancels on release, and does not restart on completion. */
public final class Trigger {
    private final Scheduler scheduler;
    private final BooleanSupplier condition;
    public Trigger(Scheduler scheduler, BooleanSupplier condition) { this.scheduler=scheduler; this.condition=condition; }
    public Trigger onTrue(Command command) { bind(command,false); return this; }
    public Trigger whileTrue(Command command) { bind(command,true); return this; }
    private void bind(Command command, boolean cancel) {
        scheduler.trigger(new Runnable() {
            boolean previous;
            public void run() {
                boolean current=condition.getAsBoolean();
                if(current && !previous) scheduler.schedule(command);
                if(!current && previous && cancel) scheduler.cancel(command);
                previous=current;
            }
        });
    }
}
