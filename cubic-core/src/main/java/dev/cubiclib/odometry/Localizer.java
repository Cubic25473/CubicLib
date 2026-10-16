package dev.cubiclib.odometry;
public interface Localizer {
    void update();
    Pose2 pose();
    void reset(Pose2 pose);
    default boolean valid(){return true;}
}
