package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import dev.cubiclib.ftc.CubicOpMode;

/** Copy with Intake.java into TeamCode; configure localization before Init. */
@TeleOp(name = "CubicLib Simple")
public final class SimpleTeleOp extends CubicOpMode {
    @Override protected void configure() {
        Intake intake = register(new Intake());
        whileHeld(() -> gamepad1.a, intake.collect());
        onPress(() -> gamepad1.b, intake.collect().withTimeout(2));
        onPressDo(() -> gamepad1.x, () -> mark("pickup-confirmed"));
    }
}
