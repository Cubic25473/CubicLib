package dev.cubiclib.recording;
import dev.cubiclib.odometry.Pose2;
public final class Sample {
    public final String type="sample";
    public final long sequence; public final double t; public final Pose2 pose;
    public final int generation; public final boolean valid; public final Double batteryVolts; public final double loopMs;
    public Sample(long sequence,double t,Pose2 pose,int generation,boolean valid,double batteryVolts,double loopMs){
        this.sequence=sequence;this.t=t;this.pose=pose;this.generation=generation;this.valid=valid;
        this.batteryVolts=Double.isFinite(batteryVolts)?batteryVolts:null;this.loopMs=Double.isFinite(loopMs)?loopMs:0;
    }
}
