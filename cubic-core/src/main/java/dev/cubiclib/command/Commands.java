package dev.cubiclib.command;
import java.util.*;
import java.util.function.*;
public final class Commands {
    private Commands() {}
    public static Command functional(String name, Runnable init, Runnable execute, BooleanSupplier done,
                                     Consumer<Boolean> end, Subsystem... requirements) {
        Set<Subsystem> req=Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(requirements)));
        if(req.contains(null)) throw new IllegalArgumentException("Null subsystem");
        return new Command() {
            public void initialize(){init.run();} public void execute(){execute.run();}
            public boolean isFinished(){return done.getAsBoolean();} public void end(boolean interrupted){end.accept(interrupted);}
            public Set<Subsystem> requirements(){return req;} public String name(){return name;}
        };
    }
    public static Command instant(Runnable action, Subsystem... req) { return functional("Instant",action,()->{},()->true,i->{},req); }
    public static Command run(Runnable action, Consumer<Boolean> end, Subsystem... req) { return functional("Run",()->{},action,()->false,end,req); }
    public static Command waitSeconds(double seconds) { return waitSeconds(seconds,System::nanoTime); }
    public static Command waitSeconds(double seconds, LongSupplier clock) {
        if(!Double.isFinite(seconds)||seconds<0) throw new IllegalArgumentException("Invalid duration");
        return new Command() {
            long start;
            public void initialize(){start=clock.getAsLong();} public void execute(){}
            public boolean isFinished(){return (clock.getAsLong()-start)/1e9>=seconds;}
            public String name(){return "Wait";}
        };
    }
    public static Command sequence(Command... children) { return new Group(0,children); }
    public static Command parallel(Command... children) { return new Group(1,children); }
    public static Command race(Command... children) { return new Group(2,children); }
    public static Command deadline(Command deadline, Command... companions) {
        Command[] all=new Command[companions.length+1]; all[0]=deadline;
        System.arraycopy(companions,0,all,1,companions.length); return new Group(3,all);
    }
    public static Command timeout(Command command, double seconds) { return race(command,waitSeconds(seconds)); }
    private static final class Group implements Command {
        final int mode; final Command[] children; final Set<Subsystem> req;
        final boolean[] running; int index; boolean done;
        Group(int mode,Command[] children) {
            this.mode=mode;this.children=children.clone(); running=new boolean[children.length];
            Set<Command> unique=Collections.newSetFromMap(new IdentityHashMap<>());
            Set<Subsystem> all=new LinkedHashSet<>();
            for(Command c:children) {
                Objects.requireNonNull(c);
                if(!unique.add(c)) throw new IllegalArgumentException("Duplicate child instance");
                if(mode!=0 && !Collections.disjoint(all,c.requirements())) throw new IllegalArgumentException("Parallel subsystem conflict");
                all.addAll(c.requirements());
            }
            req=Collections.unmodifiableSet(all);
        }
        public Set<Subsystem> requirements(){return req;}
        public void initialize() {
            index=0;done=children.length==0; Arrays.fill(running,false);
            if(mode==0) { if(!done) start(0); } else for(int i=0;i<children.length;i++) start(i);
        }
        void start(int i){running[i]=true;children[i].initialize();}
        void finish(int i,boolean interrupted){if(running[i]){running[i]=false;children[i].end(interrupted);}}
        public void execute() {
            if(done)return;
            if(mode==0) {
                children[index].execute();
                if(children[index].isFinished()) {finish(index,false);index++;if(index==children.length)done=true;else start(index);}
            } else {
                for(int i=0;i<children.length;i++) if(running[i]) {
                    children[i].execute();
                    if(children[i].isFinished()) {finish(i,false);if(mode==2 || (mode==3 && i==0)){done=true;break;}}
                }
                boolean any=false;for(boolean r:running)any|=r;if(!any)done=true;
            }
        }
        public boolean isFinished(){return done;}
        public void end(boolean interrupted){
            RuntimeException first=null;
            for(int i=0;i<children.length;i++)try{finish(i,true);}catch(RuntimeException e){if(first==null)first=e;}
            if(first!=null)throw first;
        }
        public String name(){return new String[]{"Sequence","Parallel","Race","Deadline"}[mode];}
    }
}
