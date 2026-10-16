package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.hardware.DcMotor;
import dev.cubiclib.command.Command;
import dev.cubiclib.command.Subsystem;
import static dev.cubiclib.ftc.GlobalObjects.hardwareMapEverywhere;

/** Construct inside configure(), after CubicLib makes the hardware map available. */
public final class Intake implements Subsystem {
    private final DcMotor motor = hardwareMapEverywhere.get(DcMotor.class, "intake");
    public Command collect() { return run(() -> motor.setPower(1)); }
    @Override public void stop() { motor.setPower(0); }
}
