package dev.cubiclib;

import dev.cubiclib.command.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SimpleSyntaxTest {
    static final class Motor implements Subsystem {
        double power;
        @Override public void stop(){power=0;}
    }
    @Test public void heldActionStopsOnReleaseAndDoesNotRestartWhileHeld(){
        Scheduler scheduler=new Scheduler();Motor motor=new Motor();boolean[] held={false};
        Command command=motor.run(()->motor.power=1);
        assertTrue(command.requirements().contains(motor));
        new Trigger(scheduler,()->held[0]).whileTrue(command);
        held[0]=true;scheduler.run();assertEquals(1,motor.power,0);
        scheduler.cancel(command);scheduler.run();assertEquals(0,motor.power,0);
        held[0]=false;scheduler.run();held[0]=true;scheduler.run();assertEquals(1,motor.power,0);
        held[0]=false;scheduler.run();assertEquals(0,motor.power,0);
    }
    @Test public void timeoutStopsMechanismBeforeNextAction(){
        Scheduler scheduler=new Scheduler();Motor motor=new Motor();boolean[] finished={false};
        Command command=motor.run(()->motor.power=1).withTimeout(0)
            .then(motor.once(()->{assertEquals(0,motor.power,0);finished[0]=true;}));
        scheduler.schedule(command);scheduler.run();scheduler.run();
        assertTrue(finished[0]);assertFalse(scheduler.isScheduled(command));
        assertTrue(command.requirements().contains(motor));
    }
    @Test public void replacementAndShutdownStopSimpleActions(){
        Scheduler scheduler=new Scheduler();Motor motor=new Motor();
        Command first=motor.run(()->motor.power=1), second=motor.run(()->motor.power=.5);
        scheduler.schedule(first);scheduler.run();scheduler.schedule(second);
        assertEquals(0,motor.power,0);scheduler.run();assertEquals(.5,motor.power,0);
        scheduler.stop();assertEquals(0,motor.power,0);
    }
}
