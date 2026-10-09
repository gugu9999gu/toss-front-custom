package dev.tossfront.record;

/** Frame-rate-independent turntable velocity. Retargeting preserves angle and current speed. */
final class RecordMotion {
    static final double SPEED = 22.5;
    static final double RAMP_MS = 260;
    private double angle, speed, from, target, elapsed = RAMP_MS;
    private long last = -1;

    void setPlaying(boolean playing, long now) {
        tick(now);
        double next = playing ? SPEED : 0;
        if (target == next) return;
        from = speed; target = next; elapsed = 0;
    }
    float tick(long now) {
        if (last < 0) { last = now; return (float)angle; }
        double delta = Math.max(0, now - last); last = now;
        double ramp = Math.min(delta, Math.max(0, RAMP_MS - elapsed));
        double a = elapsed / RAMP_MS, b = (elapsed + ramp) / RAMP_MS;
        // Exact integral of smoothstep; skipped frames do not change the final angle.
        double movement = from * ramp + (target - from) * RAMP_MS * (integral(b) - integral(a));
        elapsed += ramp;
        movement += target * (delta - ramp);
        angle = (angle + movement / 1000) % 360;
        double u = Math.min(1, elapsed / RAMP_MS);
        speed = from + (target - from) * u * u * (3 - 2 * u);
        return (float)angle;
    }
    private static double integral(double u) { return u * u * u - .5 * u * u * u * u; }
    boolean moving() { return target > 0 || speed > .0001 || elapsed < RAMP_MS; }
    double speed() { return speed; }
    void suspend() { last = -1; speed = from = target = 0; elapsed = RAMP_MS; }
}
