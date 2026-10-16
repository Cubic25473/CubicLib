package dev.cubiclib.odometry;
/** r = x*sin(angle)-y*cos(angle); wheel travel = u.dx+v.dy+r.dHeading. */
public final class TrackingWheel {
    public final double ux,uy,rotationCoefficient;
    private final Encoder encoder; private final double scale; private int previous;
    public TrackingWheel(Encoder encoder, PodSpec pod, boolean reversed,double xMm,double yMm,double angleRadians) {
        if(encoder==null||pod==null||!Double.isFinite(xMm)||!Double.isFinite(yMm)||!Double.isFinite(angleRadians))throw new IllegalArgumentException("Invalid wheel");
        this.encoder=encoder;scale=pod.mmPerCount()*(reversed?-1:1);
        ux=Math.cos(angleRadians);uy=Math.sin(angleRadians);rotationCoefficient=xMm*uy-yMm*ux;rebase();
    }
    public void rebase(){previous=encoder.ticks();}
    public double delta(){int now=encoder.ticks();int diff=now-previous;previous=now;return diff*scale;}
}
