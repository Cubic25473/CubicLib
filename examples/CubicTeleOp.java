package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import dev.cubiclib.command.*;
import dev.cubiclib.ftc.CubicOpMode;
import static dev.cubiclib.ftc.GlobalObjects.hardwareMapEverywhere;

/** Copy into TeamCode. Configure odometry on the dashboard before Init. */
@TeleOp(name="CubicLib TeleOp", group="CubicLib")
public final class CubicTeleOp extends CubicOpMode {
    private Drive drive;
    @Override protected void configure() {
        drive = register(new Drive());
        defaultCommand(drive, () -> drive.mecanum(
            -gamepad1.left_stick_y, -gamepad1.left_stick_x, -gamepad1.right_stick_x));
        // Driver-confirmed event markers. Replace with mechanism/sensor confirmation when available.
        onPressDo(() -> gamepad1.a, () -> mark("pickup-confirmed"));
        onPressDo(() -> gamepad1.b, () -> mark("score-confirmed"));
    }
    private static final class Drive implements Subsystem {
        final DcMotor fl=hardwareMapEverywhere.get(DcMotor.class,"frontLeft");
        final DcMotor fr=hardwareMapEverywhere.get(DcMotor.class,"frontRight");
        final DcMotor bl=hardwareMapEverywhere.get(DcMotor.class,"backLeft");
        final DcMotor br=hardwareMapEverywhere.get(DcMotor.class,"backRight");
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
