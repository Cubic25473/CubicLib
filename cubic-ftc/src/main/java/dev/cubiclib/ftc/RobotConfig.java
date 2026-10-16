package dev.cubiclib.ftc;
import dev.cubiclib.odometry.*;
import java.util.*;
public final class RobotConfig {
    public String backend="PINPOINT", pinpointName="pinpoint", imuName="imu", imuLogoFacing="UP", imuUsbFacing="FORWARD";
    public double robotLengthMm=450,robotWidthMm=450,clearanceMm=50;
    public boolean fieldGeometryConfirmed=false;
    public String fieldRevision="BIOBUZZ-TU04-nominal";
    public Pose2 startPose=new Pose2(300,300,0);
    public Wheel[] wheels={new Wheel("odoForward",0,100,0),new Wheel("odoLeft",100,0,Math.PI/2)};
    public static final class Wheel {
        public String name;public PodSpec pod=PodSpec.GOBILDA_4_BAR;public boolean reversed;
        public double xMm,yMm,angleRadians;
        public Wheel(String name,double x,double y,double angle){this.name=name;xMm=x;yMm=y;angleRadians=angle;}
    }
    public void validate(){
        if(!Arrays.asList("PINPOINT","TWO_WHEEL_IMU","THREE_WHEEL").contains(backend))throw new IllegalArgumentException("Unknown odometry backend");
        positive(robotLengthMm);positive(robotWidthMm);if(!Double.isFinite(clearanceMm)||clearanceMm<0)throw new IllegalArgumentException("Invalid clearance");
        if(robotLengthMm>3657.6||robotWidthMm>3657.6)throw new IllegalArgumentException("Robot exceeds field size");
        if(startPose==null)throw new IllegalArgumentException("Starting pose required");new Pose2(startPose.x,startPose.y,startPose.heading);
        if(wheels==null||wheels.length!=(backend.equals("THREE_WHEEL")?3:2))throw new IllegalArgumentException("Wrong number of wheels");
        Set<String> names=new HashSet<>();TrackingWheel[] check=new TrackingWheel[wheels.length];
        for(int i=0;i<wheels.length;i++){
            Wheel w=wheels[i];if(w==null||w.pod==null)throw new IllegalArgumentException("Missing wheel");w.pod.validate();
            if(w.name==null||w.name.trim().isEmpty()||!names.add(w.name))throw new IllegalArgumentException("Wheel names must be distinct");
            check[i]=new TrackingWheel(()->0,w.pod,w.reversed,w.xMm,w.yMm,w.angleRadians);
        }
        if(wheels.length==3)new DeadWheelLocalizer(check[0],check[1],check[2]);else new DeadWheelLocalizer(check[0],check[1],()->0);
        if(backend.equals("PINPOINT")){
            if(pinpointName==null||pinpointName.isEmpty())throw new IllegalArgumentException("Pinpoint name required");
            if(Math.abs(Pose2.wrap(wheels[0].angleRadians))>1e-6||Math.abs(Pose2.wrap(wheels[1].angleRadians-Math.PI/2))>1e-6)throw new IllegalArgumentException("Pinpoint needs forward then left wheels");
            if(Math.abs(wheels[0].pod.mmPerCount()-wheels[1].pod.mmPerCount())>1e-9)throw new IllegalArgumentException("This Pinpoint adapter requires matching pod scales");
        }
        if(backend.equals("TWO_WHEEL_IMU")){
            if(imuName==null||imuName.isEmpty())throw new IllegalArgumentException("IMU name required");
            com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection.valueOf(imuLogoFacing);
            com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection.valueOf(imuUsbFacing);
            new com.qualcomm.hardware.rev.RevHubOrientationOnRobot(
                com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection.valueOf(imuLogoFacing),
                com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection.valueOf(imuUsbFacing));
        }
    }
    private void positive(double n){if(!Double.isFinite(n)||n<=0)throw new IllegalArgumentException("Dimensions must be positive");}
}
