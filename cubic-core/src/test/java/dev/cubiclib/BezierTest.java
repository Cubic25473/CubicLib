package dev.cubiclib;
import dev.cubiclib.path.BezierPath;
import dev.cubiclib.odometry.Pose2;
import org.junit.Test;
import static org.junit.Assert.*;
public class BezierTest {
 private Pose2 p(double x,double y){return new Pose2(x,y,0);}
 @Test public void quadraticStrafesWithFixedHeading(){BezierPath b=BezierPath.constantHeading(0,p(0,0),p(100,100),p(0,200));assertEquals(50,b.at(.5).x,1e-9);assertEquals(100,b.at(.5).y,1e-9);assertEquals(0,b.at(.5).heading,0);assertEquals(Math.PI/2,b.tangentHeading(.5),1e-9);assertEquals(200,b.at(1).y,0);}
 @Test public void cubicMidpoint(){BezierPath b=BezierPath.constantHeading(1,p(0,0),p(0,100),p(100,100),p(100,0));assertEquals(50,b.at(.5).x,1e-9);assertEquals(75,b.at(.5).y,1e-9);assertEquals(1,b.at(.5).heading,1e-9);assertTrue(b.approximateLength(100)>100);}
 @Test public void headingWrapUsesShortArc(){BezierPath b=BezierPath.interpolateHeading(Math.toRadians(170),Math.toRadians(-170),p(0,0),p(0,100));assertEquals(Math.PI,Math.abs(b.at(.5).heading),1e-9);assertEquals(Math.toRadians(-170),b.at(1).heading,1e-9);}
 @Test public void straightLineCompatibility(){BezierPath b=BezierPath.constantHeading(0,p(0,0),p(30,40));assertEquals(50,b.approximateLength(10),1e-9);assertEquals(11,b.sample(10).size());}
 @Test(expected=IllegalArgumentException.class) public void invalidParameter(){BezierPath.constantHeading(0,p(0,0),p(1,1)).at(Double.NaN);}
 @Test public void mecanumSidewaysAtRotatedStart(){Pose2 next=new Pose2(500,600,Math.PI/2).integrate(0,100,0);assertEquals(400,next.x,1e-9);assertEquals(600,next.y,1e-9);assertEquals(Math.PI/2,next.heading,1e-9);}
}
