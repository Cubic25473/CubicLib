package dev.cubiclib.ftc;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.VoltageSensor;
import dev.cubiclib.command.*;
import java.util.function.BooleanSupplier;
import dev.cubiclib.odometry.*;
/** Extend this class for automatic scheduler, localization and TeleOp recording lifecycle. */
public abstract class CubicOpMode extends OpMode {
    protected Scheduler scheduler;protected Localizer localizer;protected CubicLib cubic;protected RobotConfig robotConfig;
    private int generation;private long previous;private boolean ownsAttachment;
    protected abstract void configure();
    /** Register once during configure(), and keep the returned subsystem. */
    protected final <T extends Subsystem> T register(T subsystem){scheduler.register(subsystem);return subsystem;}
    protected final void schedule(Command command){scheduler.schedule(command);}
    protected final void onPress(BooleanSupplier button, Command command){new Trigger(scheduler,button).onTrue(command);}
    /** For markers and other one-shot actions without subsystem requirements. */
    protected final void onPressDo(BooleanSupplier button, Runnable action){onPress(button,Commands.instant(action));}
    protected final void whileHeld(BooleanSupplier button, Command command){new Trigger(scheduler,button).whileTrue(command);}
    /** Default loop action with automatic requirements and stop-on-interruption. */
    protected final void defaultCommand(Subsystem subsystem, Runnable action){scheduler.defaultCommand(subsystem,subsystem.run(action));}
    protected void onStarted(){} protected void onLoop(){} protected void onStopped(){}
    protected Localizer createLocalizer(RobotConfig config){return Localizers.create(hardwareMap,config);}
    @Override public final void init(){
        scheduler=new Scheduler();
        try{
            cubic=CubicLib.install(hardwareMap.appContext);robotConfig=cubic.attach();ownsAttachment=true;
            GlobalObjects.bind(this,hardwareMap);
            localizer=createLocalizer(robotConfig);scheduler.onEvent((c,e)->cubic.recorder.event("command:"+c.name()+":"+e));
            configure();telemetry.addData("CubicLib","Dashboard on robot IP, port %d",CubicLib.PORT);telemetry.update();
        }catch(Exception e){cleanup();throw new IllegalStateException("CubicLib initialization failed",e);}
    }
    @Override public final void init_loop(){try{localizer.update();telemetry.addData("Localization",localizer.valid()?"Ready":"Waiting / invalid");telemetry.update();}catch(RuntimeException e){cleanup();throw e;}}
    @Override public final void start(){
        try{cubic.recorder.start(getClass().getSimpleName(),robotConfig);cubic.status="Recording TeleOp";previous=System.nanoTime();onStarted();}
        catch(RuntimeException e){cleanup();throw e;}
    }
    @Override public final void loop(){
        try{
            long now=System.nanoTime();double loopMs=(now-previous)/1e6;previous=now;
            localizer.update();scheduler.run();onLoop();
            double voltage=Double.NaN;for(VoltageSensor sensor:hardwareMap.voltageSensor){double v=sensor.getVoltage();if(v>0&&(Double.isNaN(voltage)||v<voltage))voltage=v;}
            cubic.recorder.sample(localizer.pose(),localizer.valid(),generation,voltage,loopMs);
        }catch(RuntimeException e){cleanup();throw e;}
    }
    public final void resetPose(Pose2 pose){localizer.reset(pose);generation++;if(cubic!=null)cubic.recorder.event("pose-reset");}
    public final void mark(String event){cubic.recorder.event(event);}
    @Override public final void stop(){try{onStopped();}finally{cleanup();}}
    private void cleanup(){
        try{if(scheduler!=null)scheduler.stop();}
        finally{
            try{if(cubic!=null&&ownsAttachment){
                try{cubic.recorder.stop();}finally{cubic.detach();ownsAttachment=false;}
            }}finally{GlobalObjects.clear(this);}
        }
    }
}
