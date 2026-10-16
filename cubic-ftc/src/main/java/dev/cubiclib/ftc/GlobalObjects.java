package dev.cubiclib.ftc;

import com.qualcomm.robotcore.hardware.HardwareMap;
import java.util.Objects;

/** Hardware access for the active CubicOpMode. Use only on the OpMode thread. */
public final class GlobalObjects {
    private GlobalObjects() {}
    /**
     * Set before configure(), available through stop callbacks, then cleared to null.
     * Treat as read-only. Construct subsystems in configure(), never in static initializers
     * or OpMode field initializers, and do not retain hardware across OpModes.
     */
    public static HardwareMap hardwareMapEverywhere;
    private static Object owner;

    /** Same map, with a helpful error if called before Init or after Stop. */
    public static HardwareMap requireHardwareMap() {
        if (hardwareMapEverywhere == null)
            throw new IllegalStateException("HardwareMap is available only during CubicOpMode Init through Stop. Create subsystems inside configure().");
        return hardwareMapEverywhere;
    }

    static void bind(Object opMode, HardwareMap map) {
        Objects.requireNonNull(opMode, "OpMode");
        Objects.requireNonNull(map, "HardwareMap");
        if (owner != null && owner != opMode)
            throw new IllegalStateException("Another CubicOpMode owns the HardwareMap");
        owner = opMode;
        hardwareMapEverywhere = map;
    }

    static void clear(Object opMode) {
        if (owner == opMode) {
            hardwareMapEverywhere = null;
            owner = null;
        }
    }
}
