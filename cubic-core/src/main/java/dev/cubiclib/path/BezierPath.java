package dev.cubiclib.path;

import dev.cubiclib.odometry.Pose2;
import java.util.*;

/** Immutable Bezier geometry. Heading is independent of the curve tangent (holonomic drive).
 * Parameter t is not elapsed time or distance. This class does not command motors.
 */
public final class BezierPath {
    private final Pose2[] points;
    private final double startHeading, headingDelta;
    private BezierPath(double start, double end, Pose2... points) {
        if(points==null||points.length<2||points.length>4)throw new IllegalArgumentException("Use 2, 3 or 4 control points");
        if(!Double.isFinite(start)||!Double.isFinite(end))throw new IllegalArgumentException("Invalid heading");
        this.points=points.clone();for(Pose2 p:this.points)Objects.requireNonNull(p);
        startHeading=Pose2.wrap(start);headingDelta=Pose2.wrap(end-start);
    }
    /** Control-point headings are ignored; only x/y define geometry. */
    public static BezierPath constantHeading(double radians, Pose2... points){return new BezierPath(radians,radians,points);}
    /** Interpolates robot heading along the shortest angular arc, independently of translation. */
    public static BezierPath interpolateHeading(double startRadians,double endRadians,Pose2... points){return new BezierPath(startRadians,endRadians,points);}
    public Pose2 at(double t){
        check(t);double[] x=new double[points.length],y=new double[points.length];
        for(int i=0;i<points.length;i++){x[i]=points[i].x;y[i]=points[i].y;}
        for(int n=points.length-1;n>0;n--)for(int i=0;i<n;i++){x[i]=(1-t)*x[i]+t*x[i+1];y[i]=(1-t)*y[i]+t*y[i+1];}
        return new Pose2(x[0],y[0],startHeading+headingDelta*t);
    }
    /** Returns tangent direction, not robot heading. Undefined at a stationary point. */
    public double tangentHeading(double t){
        check(t);int degree=points.length-1;double[] x=new double[degree],y=new double[degree];
        for(int i=0;i<degree;i++){x[i]=degree*(points[i+1].x-points[i].x);y[i]=degree*(points[i+1].y-points[i].y);}
        for(int n=degree-1;n>0;n--)for(int i=0;i<n;i++){x[i]=(1-t)*x[i]+t*x[i+1];y[i]=(1-t)*y[i]+t*y[i+1];}
        if(Math.hypot(x[0],y[0])<1e-12)throw new IllegalStateException("Stationary tangent");
        return Math.atan2(y[0],x[0]);
    }
    public List<Pose2> sample(int segments){
        if(segments<1||segments>100000)throw new IllegalArgumentException("segments must be 1..100000");
        List<Pose2> result=new ArrayList<>();for(int i=0;i<=segments;i++)result.add(at((double)i/segments));return Collections.unmodifiableList(result);
    }
    /** Polyline approximation; increase segments for more precision. */
    public double approximateLength(int segments){List<Pose2> p=sample(segments);double sum=0;for(int i=1;i<p.size();i++)sum+=Math.hypot(p.get(i).x-p.get(i-1).x,p.get(i).y-p.get(i-1).y);return sum;}
    private static void check(double t){if(!Double.isFinite(t)||t<0||t>1)throw new IllegalArgumentException("t must be within [0,1]");}
}
