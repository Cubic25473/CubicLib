package dev.cubiclib.odometry;
/** Millimeters, radians. Field +X right, +Y up; heading CCW from +X. Robot +x forward, +y left. */
public final class Pose2 {
    public final double x,y,heading;
    public Pose2(double x,double y,double heading) {
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(heading))throw new IllegalArgumentException("Non-finite pose");
        this.x=x;this.y=y;this.heading=wrap(heading);
    }
    public static double wrap(double a){return Math.atan2(Math.sin(a),Math.cos(a));}
    /** SE(2) exponential: integrates a robot-relative twist, including finite turns. */
    public Pose2 integrate(double dx,double dy,double dh) {
        double a=Math.abs(dh)<1e-8 ? 1-dh*dh/6 : Math.sin(dh)/dh;
        double b=Math.abs(dh)<1e-8 ? dh/2 : (1-Math.cos(dh))/dh;
        double u=a*dx-b*dy,v=b*dx+a*dy,c=Math.cos(heading),s=Math.sin(heading);
        return new Pose2(x+c*u-s*v,y+s*u+c*v,heading+dh);
    }
}
