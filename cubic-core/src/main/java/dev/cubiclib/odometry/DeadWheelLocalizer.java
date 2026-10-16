package dev.cubiclib.odometry;
import java.util.function.DoubleSupplier;
/** Two arbitrary nonparallel wheels + IMU, or three independent tracking wheels. */
public final class DeadWheelLocalizer implements Localizer {
    private final TrackingWheel[] wheels; private final DoubleSupplier imu; private final double[][] inverse;
    private Pose2 pose=new Pose2(0,0,0);private double previousHeading;private boolean valid=true,needsReset;
    public DeadWheelLocalizer(TrackingWheel a,TrackingWheel b,DoubleSupplier headingRadians) {
        this(new TrackingWheel[]{a,b},java.util.Objects.requireNonNull(headingRadians));
    }
    public DeadWheelLocalizer(TrackingWheel a,TrackingWheel b,TrackingWheel c){this(new TrackingWheel[]{a,b,c},null);}
    private DeadWheelLocalizer(TrackingWheel[] w,DoubleSupplier imu) {
        wheels=w;this.imu=imu;
        double[][] m=new double[3][3];
        for(int i=0;i<w.length;i++) {m[i][0]=w[i].ux;m[i][1]=w[i].uy;m[i][2]=w[i].rotationCoefficient;}
        if(imu!=null)m[2][2]=1;
        inverse=invert(m);reset(pose);
    }
    public void update() {
        if(needsReset){for(TrackingWheel w:wheels)w.rebase();valid=false;return;}
        double heading=imu==null?0:imu.getAsDouble();
        if(!Double.isFinite(heading)) {valid=false;needsReset=true;for(TrackingWheel w:wheels)w.rebase();previousHeading=Double.NaN;return;}
        if(imu!=null&&!Double.isFinite(previousHeading)){previousHeading=heading;for(TrackingWheel w:wheels)w.rebase();valid=false;return;}
        double[] d=new double[3];for(int i=0;i<wheels.length;i++)d[i]=wheels[i].delta();
        if(imu!=null){d[2]=Pose2.wrap(heading-previousHeading);previousHeading=heading;}
        double[] t=new double[3];for(int i=0;i<3;i++)for(int j=0;j<3;j++)t[i]+=inverse[i][j]*d[j];
        pose=pose.integrate(t[0],t[1],t[2]);valid=true;
    }
    public Pose2 pose(){return pose;}
    public boolean valid(){return valid;}
    public void reset(Pose2 p){pose=java.util.Objects.requireNonNull(p);for(TrackingWheel w:wheels)w.rebase();previousHeading=imu==null?0:imu.getAsDouble();valid=Double.isFinite(previousHeading);needsReset=!valid;}
    private static double[][] invert(double[][] m) {
        double[][] a=new double[3][6];for(int i=0;i<3;i++){System.arraycopy(m[i],0,a[i],0,3);a[i][i+3]=1;}
        for(int col=0;col<3;col++) {
            int pivot=col;for(int i=col+1;i<3;i++)if(Math.abs(a[i][col])>Math.abs(a[pivot][col]))pivot=i;
            if(Math.abs(a[pivot][col])<1e-8)throw new IllegalArgumentException("Tracking wheel geometry is singular");
            double[] swap=a[col];a[col]=a[pivot];a[pivot]=swap;
            double div=a[col][col];for(int j=0;j<6;j++)a[col][j]/=div;
            for(int i=0;i<3;i++)if(i!=col){double f=a[i][col];for(int j=0;j<6;j++)a[i][j]-=f*a[col][j];}
        }
        double[][] result=new double[3][3];for(int i=0;i<3;i++)System.arraycopy(a[i],3,result[i],0,3);return result;
    }
}
