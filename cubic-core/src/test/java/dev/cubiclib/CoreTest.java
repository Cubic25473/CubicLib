package dev.cubiclib;
import dev.cubiclib.command.*;
import dev.cubiclib.odometry.*;
import dev.cubiclib.recording.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.file.*;
public class CoreTest {
    static class Motor implements Subsystem {int stops;public void stop(){stops++;}}
    @Test public void conflictInterruptsBeforeReplacementAndStopIsIdempotent(){
        Scheduler s=new Scheduler();Motor motor=new Motor();List<String> log=new ArrayList<>();
        Command a=Commands.functional("a",()->log.add("a init"),()->{},()->false,i->log.add("a end "+i),motor);
        Command b=Commands.functional("b",()->log.add("b init"),()->{},()->false,i->log.add("b end "+i),motor);
        s.schedule(a);s.schedule(b);s.stop();s.stop();s.schedule(a);
        assertEquals(Arrays.asList("a init","a end true","b init","b end true"),log);assertEquals(1,motor.stops);assertFalse(s.isScheduled(a));
    }
    @Test public void completedCommandEndsOnceAndDefaultDoesNotRestartOnStop(){
        Scheduler s=new Scheduler();Motor m=new Motor();int[] calls={0};Command c=Commands.instant(()->calls[0]++,m);s.defaultCommand(m,c);
        s.run();s.run();assertEquals(2,calls[0]);s.stop();s.run();assertEquals(2,calls[0]);
    }
    @Test public void deferredSchedulingFromExecuteInterruptsCleanly(){
        Scheduler s=new Scheduler();Motor m=new Motor();int[] ends={0};Command b=Commands.run(()->{},i->{},m);
        Command a=Commands.run(()->s.schedule(b),i->ends[0]++,m);s.schedule(a);s.run();assertEquals(1,ends[0]);assertTrue(s.isScheduled(b));
    }
    @Test public void exceptionStopsAllSubsystemsEvenWhenCleanupFails(){
        Scheduler s=new Scheduler();Motor a=new Motor(),b=new Motor();s.register(a,b);
        s.schedule(Commands.run(()->{throw new IllegalStateException("execute");},i->{throw new IllegalArgumentException("cleanup");},a));
        try{s.run();fail();}catch(IllegalStateException e){assertEquals("execute",e.getMessage());assertEquals(1,e.getSuppressed().length);}
        assertEquals(1,a.stops);assertEquals(1,b.stops);
    }
    @Test public void sequenceAndRaceLifecycle(){
        Scheduler s=new Scheduler();List<String> events=new ArrayList<>();AtomicLong clock=new AtomicLong();
        Command run=Commands.run(()->events.add("run"),i->events.add("end "+i));
        Command group=Commands.sequence(Commands.instant(()->events.add("first")),Commands.race(run,Commands.waitSeconds(1,clock::get)));
        s.schedule(group);s.run();s.run();clock.set(1_000_000_000L);s.run();assertFalse(s.isScheduled(group));assertEquals("first",events.get(0));assertEquals("end true",events.get(events.size()-1));
        assertEquals(1,events.stream().filter(x->x.equals("end true")).count());
    }
    @Test public void deadlineInterruptsCompanion(){
        Scheduler s=new Scheduler();int[] ends={0};Command c=Commands.deadline(Commands.instant(()->{}),Commands.run(()->{},i->{assertTrue(i);ends[0]++;}));
        s.schedule(c);s.run();assertEquals(1,ends[0]);assertFalse(s.isScheduled(c));
    }
    @Test(expected=IllegalArgumentException.class) public void parallelRejectsConflictingRequirements(){Motor m=new Motor();Commands.parallel(Commands.run(()->{},i->{},m),Commands.instant(()->{},m));}
    @Test public void triggerOnlyFiresOnEdges(){Scheduler s=new Scheduler();boolean[] pressed={false};int[] count={0};new Trigger(s,()->pressed[0]).onTrue(Commands.instant(()->count[0]++));s.run();pressed[0]=true;s.run();s.run();assertEquals(1,count[0]);pressed[0]=false;s.run();pressed[0]=true;s.run();assertEquals(2,count[0]);}
    @Test public void podPresetsAndGearing(){assertEquals(Math.PI*32/2000,PodSpec.GOBILDA_4_BAR.mmPerCount(),1e-12);assertEquals(Math.PI*48/2000,PodSpec.GOBILDA_SWINGARM.mmPerCount(),1e-12);assertEquals(.5,new PodSpec(100/Math.PI,100,2).mmPerCount(),1e-12);}
    @Test(expected=IllegalArgumentException.class) public void badCountsRejected(){new PodSpec(32,0,1);}
    @Test public void twoWheelRotationAndReset(){
        int[] ticks={0,0};double[] yaw={0};PodSpec p=new PodSpec(1/Math.PI,1,1);
        DeadWheelLocalizer l=new DeadWheelLocalizer(new TrackingWheel(()->ticks[0],p,false,0,100,0),new TrackingWheel(()->ticks[1],p,false,100,0,Math.PI/2),()->yaw[0]);
        ticks[0]=500;ticks[1]=250;l.update();assertEquals(500,l.pose().x,1e-9);assertEquals(250,l.pose().y,1e-9);
        ticks[0]-=100;ticks[1]+=100;yaw[0]=1;l.update();assertEquals(500,l.pose().x,1e-9);assertEquals(250,l.pose().y,1e-9);assertEquals(1,l.pose().heading,1e-9);
        l.reset(new Pose2(20,30,0));l.update();assertEquals(20,l.pose().x,1e-9);assertEquals(30,l.pose().y,1e-9);
    }
    @Test public void threeWheelRotationAndTranslation(){
        int[] t={0,0,0};PodSpec p=new PodSpec(1/Math.PI,1,1);
        DeadWheelLocalizer l=new DeadWheelLocalizer(new TrackingWheel(()->t[0],p,false,0,100,0),new TrackingWheel(()->t[1],p,false,0,-100,0),new TrackingWheel(()->t[2],p,false,80,0,Math.PI/2));
        t[0]=-100;t[1]=100;t[2]=80;l.update();assertEquals(0,l.pose().x,1e-9);assertEquals(0,l.pose().y,1e-9);assertEquals(1,l.pose().heading,1e-9);
        t[0]+=100;t[1]+=100;l.update();assertEquals(100*Math.cos(1),l.pose().x,1e-9);assertEquals(100*Math.sin(1),l.pose().y,1e-9);
    }
    @Test public void encoderWrapAndReverse(){int[] t={Integer.MAX_VALUE-5};TrackingWheel w=new TrackingWheel(()->t[0],new PodSpec(1/Math.PI,1,1),true,0,0,0);t[0]=Integer.MIN_VALUE+4;assertEquals(-10,w.delta(),1e-9);}
    @Test public void headingWrapUsesShortRotation(){int[] t={0,0};double[] yaw={Math.PI-.01};PodSpec p=new PodSpec(1/Math.PI,1,1);DeadWheelLocalizer l=new DeadWheelLocalizer(new TrackingWheel(()->t[0],p,false,0,0,0),new TrackingWheel(()->t[1],p,false,0,0,Math.PI/2),()->yaw[0]);yaw[0]=-Math.PI+.01;l.update();assertEquals(.02,l.pose().heading,1e-9);}
    @Test public void nonfiniteImuDoesNotPoisonPose(){double[] h={0};TrackingWheel a=new TrackingWheel(()->0,PodSpec.GOBILDA_4_BAR,false,0,0,0),b=new TrackingWheel(()->0,PodSpec.GOBILDA_4_BAR,false,0,0,Math.PI/2);DeadWheelLocalizer l=new DeadWheelLocalizer(a,b,()->h[0]);h[0]=Double.NaN;l.update();assertFalse(l.valid());h[0]=1;l.update();assertFalse(l.valid());l.update();assertFalse(l.valid());l.reset(new Pose2(0,0,0));l.update();assertTrue(l.valid());assertEquals(0,l.pose().heading,0);}
    @Test(expected=IllegalArgumentException.class) public void parallelWheelsNeedHeadingIndependentAxis(){new DeadWheelLocalizer(new TrackingWheel(()->0,PodSpec.GOBILDA_4_BAR,false,0,0,0),new TrackingWheel(()->0,PodSpec.GOBILDA_4_BAR,false,0,100,0),()->0);}
    @Test public void finiteTurnIntegration(){Pose2 p=new Pose2(0,0,0).integrate(100,0,Math.PI/2);assertEquals(200/Math.PI,p.x,1e-9);assertEquals(200/Math.PI,p.y,1e-9);}
    @Test public void recorderWritesWithoutBrowserAndFinalizes()throws Exception{
        Path folder=Files.createTempDirectory("cubic-recording");SessionRecorder r=new SessionRecorder(folder.toFile());r.start("Test",Collections.singletonMap("fieldRevision","test"));r.sample(new Pose2(1,2,3),true,0,Double.NaN,20);r.event("pickup-confirmed");String id=r.activeId();r.stop();r.writerThread().join(2000);assertFalse(r.writerThread().isAlive());
        List<String> lines=Files.readAllLines(r.sessionFile(id).toPath());assertEquals(4,lines.size());assertTrue(lines.get(0).contains("\"schema\":1"));assertTrue(lines.get(1).contains("\"sample\""));assertTrue(lines.get(3).contains("\"end\""));assertFalse(lines.toString().contains("NaN"));
        r.start("Second",null);r.stop();r.writerThread().join(2000);assertEquals(2,r.sessions().size());
        for(java.io.File f:folder.toFile().listFiles())f.delete();Files.delete(folder);
    }
    @Test(expected=IllegalArgumentException.class) public void sessionPathTraversalRejected()throws Exception{SessionRecorder r=new SessionRecorder(Files.createTempDirectory("cubic-path").toFile());r.sessionFile("../robot.json");}
}
