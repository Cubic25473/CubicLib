package dev.cubiclib.odometry;
/** Counts are decoded counts, not unqualified vendor PPR. Ratio = encoder revs / wheel rev. */
public final class PodSpec {
    public final double wheelDiameterMm, countsPerEncoderRev, encoderRevsPerWheelRev;
    public static final PodSpec GOBILDA_4_BAR=new PodSpec(32,2000,1);
    public static final PodSpec GOBILDA_SWINGARM=new PodSpec(48,2000,1);
    public PodSpec(double diameter,double counts,double ratio) {
        positive(diameter);positive(counts);positive(ratio);
        wheelDiameterMm=diameter;countsPerEncoderRev=counts;encoderRevsPerWheelRev=ratio;
    }
    public void validate(){positive(wheelDiameterMm);positive(countsPerEncoderRev);positive(encoderRevsPerWheelRev);}
    public double mmPerCount(){validate();return Math.PI*wheelDiameterMm/(countsPerEncoderRev*encoderRevsPerWheelRev);}
    private static void positive(double n){if(!Double.isFinite(n)||n<=0)throw new IllegalArgumentException("Pod values must be finite and positive");}
}
