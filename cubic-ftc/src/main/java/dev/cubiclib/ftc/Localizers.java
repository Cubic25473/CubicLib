package dev.cubiclib.ftc;
import com.qualcomm.robotcore.hardware.*;
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import org.firstinspires.ftc.robotcore.external.navigation.*;
import dev.cubiclib.odometry.*;
public final class Localizers {
    private Localizers(){}
    public static Localizer create(HardwareMap hardware,RobotConfig config){
        config.validate();Localizer result;
        if(config.backend.equals("PINPOINT"))return new Pinpoint(hardware.get(GoBildaPinpointDriver.class,config.pinpointName),config);
        TrackingWheel[] wheels=new TrackingWheel[config.wheels.length];
        for(int i=0;i<wheels.length;i++){
            RobotConfig.Wheel w=config.wheels[i];DcMotorEx motor=hardware.get(DcMotorEx.class,w.name);
            // SDK getCurrentPosition includes the motor direction; use dedicated, unchanged encoder channels.
            wheels[i]=new TrackingWheel(motor::getCurrentPosition,w.pod,w.reversed,w.xMm,w.yMm,w.angleRadians);
        }
        if(wheels.length==3)result=new DeadWheelLocalizer(wheels[0],wheels[1],wheels[2]);
        else {
            IMU imu=hardware.get(IMU.class,config.imuName);
            imu.initialize(new IMU.Parameters(new RevHubOrientationOnRobot(
                RevHubOrientationOnRobot.LogoFacingDirection.valueOf(config.imuLogoFacing),RevHubOrientationOnRobot.UsbFacingDirection.valueOf(config.imuUsbFacing))));
            result=new DeadWheelLocalizer(wheels[0],wheels[1],()->imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.RADIANS));
        }
        result.reset(config.startPose);return result;
    }
    private static final class Pinpoint implements Localizer {
        final GoBildaPinpointDriver driver;Pose2 pose,pending;boolean valid;
        Pinpoint(GoBildaPinpointDriver d,RobotConfig c){
            driver=d;pose=c.startPose;pending=pose;
            d.setOffsets(c.wheels[0].yMm,c.wheels[1].xMm,DistanceUnit.MM);
            PodSpec p=c.wheels[0].pod;
            if(p.wheelDiameterMm==32&&p.countsPerEncoderRev==2000&&p.encoderRevsPerWheelRev==1)d.setEncoderResolution(GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_4_BAR_POD);
            else if(p.wheelDiameterMm==48&&p.countsPerEncoderRev==2000&&p.encoderRevsPerWheelRev==1)d.setEncoderResolution(GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_SWINGARM_POD);
            else d.setEncoderResolution(1/p.mmPerCount(),DistanceUnit.MM);
            d.setEncoderDirections(c.wheels[0].reversed?GoBildaPinpointDriver.EncoderDirection.REVERSED:GoBildaPinpointDriver.EncoderDirection.FORWARD,
                c.wheels[1].reversed?GoBildaPinpointDriver.EncoderDirection.REVERSED:GoBildaPinpointDriver.EncoderDirection.FORWARD);
            d.resetPosAndIMU();
        }
        public void update(){
            driver.update();valid=driver.getDeviceStatus()==GoBildaPinpointDriver.DeviceStatus.READY;
            if(!valid)return;
            if(pending!=null){driver.setPosition(new Pose2D(DistanceUnit.MM,pending.x,pending.y,AngleUnit.RADIANS,pending.heading));pose=pending;pending=null;valid=false;return;}
            Pose2D p=driver.getPosition();
            double x=p.getX(DistanceUnit.MM),y=p.getY(DistanceUnit.MM),h=p.getHeading(AngleUnit.RADIANS);
            valid=Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(h);if(valid)pose=new Pose2(x,y,h);
        }
        public Pose2 pose(){return pose;}public boolean valid(){return valid;}
        public void reset(Pose2 p){pending=java.util.Objects.requireNonNull(p);pose=p;valid=false;}
    }
}
