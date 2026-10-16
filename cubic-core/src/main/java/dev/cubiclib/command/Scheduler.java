package dev.cubiclib.command;
import java.util.*;
import java.util.function.BiConsumer;
/** Single-threaded, deterministic scheduler. Incoming commands interrupt conflicts. */
public final class Scheduler {
    private final Map<Command, Set<Subsystem>> active = new LinkedHashMap<>();
    private final Set<Subsystem> subsystems = new LinkedHashSet<>();
    private final Map<Subsystem, Command> defaults = new LinkedHashMap<>();
    private final List<Runnable> triggers = new ArrayList<>();
    private final Queue<Runnable> pending = new ArrayDeque<>();
    private boolean busy, stopped;
    private BiConsumer<Command,String> listener = (c,e) -> {};
    public void onEvent(BiConsumer<Command,String> listener) { this.listener = Objects.requireNonNull(listener); }
    public void register(Subsystem... systems) { subsystems.addAll(Arrays.asList(systems)); }
    public void defaultCommand(Subsystem subsystem, Command command) {
        if (!command.requirements().contains(subsystem)) throw new IllegalArgumentException("Default must require its subsystem");
        register(subsystem); defaults.put(subsystem, command);
    }
    public void trigger(Runnable poll) { triggers.add(Objects.requireNonNull(poll)); }
    public boolean isScheduled(Command c) { return active.containsKey(c); }
    public void schedule(Command c) { Objects.requireNonNull(c); mutate(() -> start(c)); }
    public void cancel(Command c) { mutate(() -> finish(c, true)); }
    private void mutate(Runnable action) {
        if (busy) { pending.add(action); return; }
        busy = true;
        try { action.run(); drain(); } catch (RuntimeException e) {
            try { emergencyStop(); } catch (RuntimeException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
        finally { busy = false; }
    }
    private void drain() {
        int count = 0;
        while (!pending.isEmpty()) {
            if (++count > 1000) throw new IllegalStateException("Recursive scheduling loop");
            pending.remove().run();
        }
    }
    private void start(Command c) {
        if (stopped || active.containsKey(c)) return;
        Set<Subsystem> requirements = new LinkedHashSet<>(c.requirements());
        if (requirements.contains(null)) throw new IllegalArgumentException("Null subsystem");
        for (Command old : new ArrayList<>(active.keySet()))
            if (!Collections.disjoint(requirements, active.get(old))) finish(old, true);
        subsystems.addAll(requirements); active.put(c, requirements);
        c.initialize(); listener.accept(c,"start");
    }
    private void finish(Command c, boolean interrupted) {
        if (active.remove(c) == null) return;
        c.end(interrupted); listener.accept(c, interrupted ? "interrupted" : "complete");
    }
    public void run() {
        if (stopped) return;
        if (busy) throw new IllegalStateException("Scheduler reentry");
        mutate(() -> {
            for (Subsystem s : new ArrayList<>(subsystems)) s.periodic();
            for (Runnable t : new ArrayList<>(triggers)) t.run();
            drain();
            for (Command c : new ArrayList<>(active.keySet())) {
                if (!active.containsKey(c)) continue;
                c.execute();
                if (c.isFinished()) finish(c,false);
                drain();
            }
            for (Command c : new LinkedHashSet<>(defaults.values())) {
                boolean free = true;
                for (Set<Subsystem> used : active.values())
                    if (!Collections.disjoint(used,c.requirements())) free = false;
                if (free) start(c);
            }
        });
    }
    /** Permanently stops this scheduler. Construct a new one for the next OpMode. */
    public void stop() { mutate(this::emergencyStop); }
    private void emergencyStop() {
        if (stopped) return;
        stopped = true; pending.clear();
        RuntimeException first = null;
        for (Command c : new ArrayList<>(active.keySet())) try { finish(c,true); } catch (RuntimeException e) { if(first==null) first=e; }
        for (Subsystem s : subsystems) try { s.stop(); } catch (RuntimeException e) { if(first==null) first=e; }
        pending.clear();
        if (first != null) throw first;
    }
}
