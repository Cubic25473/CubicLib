package dev.cubiclib.ftc;

import com.qualcomm.robotcore.hardware.HardwareMap;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import dev.cubiclib.command.Scheduler;
import dev.cubiclib.command.Subsystem;
import dev.cubiclib.odometry.Localizer;

public class GlobalObjectsTest {
    private final Object first=new Object(), second=new Object();
    @After public void cleanup(){GlobalObjects.clear(first);GlobalObjects.clear(second);}
    @Test public void accessOutsideLifecycleHasHelpfulError(){
        assertNull(GlobalObjects.hardwareMapEverywhere);
        try{GlobalObjects.requireHardwareMap();fail();}
        catch(IllegalStateException e){assertTrue(e.getMessage().contains("configure()"));}
    }
    @Test public void mapIsReplacedBetweenOpModesAndStaleCleanupCannotClearIt(){
        HardwareMap a=mock(HardwareMap.class),b=mock(HardwareMap.class);
        GlobalObjects.bind(first,a);assertSame(a,GlobalObjects.hardwareMapEverywhere);
        assertSame(a,GlobalObjects.requireHardwareMap());
        GlobalObjects.clear(first);assertNull(GlobalObjects.hardwareMapEverywhere);
        GlobalObjects.bind(second,b);GlobalObjects.clear(first);
        assertSame(b,GlobalObjects.requireHardwareMap());
        GlobalObjects.clear(second);GlobalObjects.clear(second);assertNull(GlobalObjects.hardwareMapEverywhere);
    }
    @Test public void anotherOwnerCannotOverwriteActiveHardware(){
        HardwareMap a=mock(HardwareMap.class);
        GlobalObjects.bind(first,a);
        try{GlobalObjects.bind(second,mock(HardwareMap.class));fail();}catch(IllegalStateException expected){}
        GlobalObjects.clear(second);assertSame(a,GlobalObjects.requireHardwareMap());
    }
    @Test public void stopKeepsMapDuringCallbacksAndClearsEvenWhenSubsystemThrows(){
        CubicOpMode op=mock(CubicOpMode.class,CALLS_REAL_METHODS);
        HardwareMap map=mock(HardwareMap.class);
        op.scheduler=new Scheduler();
        op.scheduler.register(new Subsystem(){public void stop(){
            assertSame(map,GlobalObjects.requireHardwareMap());
            throw new IllegalStateException("stop failed");
        }});
        GlobalObjects.bind(op,map);
        try{op.stop();fail();}catch(IllegalStateException expected){assertEquals("stop failed",expected.getMessage());}
        finally{try{assertNull(GlobalObjects.hardwareMapEverywhere);}finally{GlobalObjects.clear(op);}}
    }
    @Test public void loopFailureClearsHardwareAndStopsMechanisms(){
        CubicOpMode op=mock(CubicOpMode.class,CALLS_REAL_METHODS);
        op.scheduler=new Scheduler();boolean[] stopped={false};
        op.scheduler.register(new Subsystem(){public void stop(){stopped[0]=true;}});
        op.localizer=mock(Localizer.class);
        doThrow(new IllegalStateException("sensor failed")).when(op.localizer).update();
        GlobalObjects.bind(op,mock(HardwareMap.class));
        try{op.loop();fail();}catch(IllegalStateException expected){assertEquals("sensor failed",expected.getMessage());}
        finally{assertNull(GlobalObjects.hardwareMapEverywhere);GlobalObjects.clear(op);}
        assertTrue(stopped[0]);
    }

}
