package dev.tossfront.record;

public final class RecordMotionTest {
    private static int count;
    private static void check(boolean value, String message) { count++; if (!value) throw new AssertionError(message); }
    private static boolean near(double a, double b) { return Math.abs(a - b) < .0001; }
    public static void main(String[] args) {
        RecordMotion smooth = new RecordMotion(); smooth.setPlaying(true, 0);
        check(near(smooth.tick(0),0), "Start must preserve angle");
        smooth.tick(130); check(near(smooth.speed(),11.25), "Startup must accelerate");
        smooth.tick(260); check(near(smooth.speed(),22.5), "Startup reaches steady speed");
        check(near(smooth.tick(1260),25.425), "Angle must follow elapsed time");
        smooth.setPlaying(false,1260); smooth.tick(1390); check(near(smooth.speed(),11.25), "Pause must coast down");
        float stopped = smooth.tick(1520); check(!smooth.moving(), "Paused rotation must settle");
        check(near(smooth.tick(9000),stopped), "Paused clock must not rotate");
        RecordMotion coarse = new RecordMotion(), fine = new RecordMotion();
        coarse.setPlaying(true,0); fine.setPlaying(true,0);
        for (int i=16;i<1000;i+=16) fine.tick(i);
        check(near(coarse.tick(1000),fine.tick(1000)), "Skipped frames must preserve angular distance");
        coarse.setPlaying(false,1000); float at=coarse.tick(1060); double speed=coarse.speed();
        coarse.setPlaying(true,1060); check(near(coarse.tick(1060),at)&&near(coarse.speed(),speed), "Rapid reversal must preserve angle and velocity");
        coarse.suspend(); float held=coarse.tick(30000); check(near(coarse.tick(60000),held), "Hidden views must not advance");
        coarse.setPlaying(true,60000); check(near(coarse.tick(60000),held), "Resume must not jump");
        SpectrumData data = new SpectrumData(); byte[] samples=new byte[512]; java.util.Arrays.fill(samples,(byte)128); data.waveform(samples);
        check(data.peak==0, "Silence must not invent motion");
        samples[256]=(byte)255; data.waveform(samples); check(data.peak>0, "Actual samples must produce signal");
        byte[] fft=new byte[512]; fft[64]=100; data.fft(fft); float max=0;for(float band:data.bands)max=Math.max(max,band);
        check(max>0&&max<=1, "FFT response must be finite and bounded");
        data.clear();check(data.peak==0&&data.bands[0]==0&&data.waveform[64]==0,"Stop must clear captured data");
        System.out.println("Motion/audio contracts: "+count+" assertions passed");
    }
}
