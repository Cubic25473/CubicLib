package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import dev.cubiclib.command.*;
import dev.cubiclib.ftc.CubicOpMode;

/** Copy into TeamCode. Configure odometry on the dashboard before Init. */
@TeleOp(name="CubicLib TeleOp", group="CubicLib")
public final class CubicTeleOp extends CubicOpMode {
    private Drive drive;
    @Override protected void configure() {
        drive = new Drive();
        scheduler.register(drive);
        scheduler.defaultCommand(drive, Commands.run(() -> drive.mecanum(
            -gamepad1.left_stick_y, -gamepad1.left_stick_x, -gamepad1.right_stick_x),
            interrupted -> drive.stop(), drive));
        // Driver-confirmed event markers. Replace with mechanism/sensor confirmation when available.
        new Trigger(scheduler, () -> gamepad1.a).onTrue(Commands.instant(() -> mark("pickup-confirmed")));
        new Trigger(scheduler, () -> gamepad1.b).onTrue(Commands.instant(() -> mark("score-confirmed")));
    }
    private final class Drive implements Subsystem {
        final DcMotor fl=hardwareMap.get(DcMotor.class,"frontLeft");
        final DcMotor fr=hardwareMap.get(DcMotor.class,"frontRight");
        final DcMotor bl=hardwareMap.get(DcMotor.class,"backLeft");
        final DcMotor br=hardwareMap.get(DcMotor.class,"backRight");
        Drive(){
            // Common arrangement; verify all four directions with wheels lifted before driving.
            fl.setDirection(DcMotorSimple.Direction.REVERSE);
            bl.setDirection(DcMotorSimple.Direction.REVERSE);
            for(DcMotor m:new DcMotor[]{fl,fr,bl,br})m.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        }
        void mecanum(double forward,double left,double ccw){
            double a=forward-left-ccw,b=forward+left+ccw,c=forward+left-ccw,d=forward-left+ccw;
            double denominator=Math.max(1,Math.max(Math.max(Math.abs(a),Math.abs(b)),Math.max(Math.abs(c),Math.abs(d))));
            fl.setPower(a/denominator);fr.setPower(b/denominator);bl.setPower(c/denominator);br.setPower(d/denominator);
        }
        public void stop(){for(DcMotor m:new DcMotor[]{fl,fr,bl,br})m.setPower(0);}
    }
}
