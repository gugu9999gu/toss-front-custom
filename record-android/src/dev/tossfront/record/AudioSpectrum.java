package dev.tossfront.record;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.audiofx.Visualizer;
import android.os.SystemClock;
import android.util.Log;

/** Analyzes Android's output mix, never the microphone. No recordings, network or sample files. */
final class AudioSpectrum {
    private final Context context;
    private Visualizer visualizer;
    final SpectrumData data = new SpectrumData();
    final GestureInfluence gesture=new GestureInfluence();
    private boolean failed, signalReported;
    private long started,lastSignal,retryAt;
    private int failures,silentRetries;
    private String status = "재생을 기다리는 중";
    AudioSpectrum(Context c) { context = c.getApplicationContext(); }
    boolean hasPermission() { return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED; }
    void start() {
        long now=SystemClock.elapsedRealtime();
        if(visualizer!=null){if(now-Math.max(started,lastSignal)>3000&&silentRetries<3){silentRetries++;stop();retryAt=now+500;Log.i("FrontRecordAudio","output_mix_signal_retry");}else return;}
        if(now<retryAt||failed&&failures>=3)return;
        if (!hasPermission()) { status = "오디오 분석 권한이 필요해요"; return; }
        try {
            Visualizer candidate = new Visualizer(0);
            visualizer = candidate;
            int[] range = Visualizer.getCaptureSizeRange();
            int size = Math.max(range[0], Math.min(range[1], 1024));
            if (candidate.setCaptureSize(size) != Visualizer.SUCCESS) throw new IllegalStateException("capture size");
            candidate.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED);
            int result = candidate.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                public void onWaveFormDataCapture(Visualizer v, byte[] samples, int rate) {
                    data.sampling(rate,samples.length);data.waveform(samples);
                    if(data.peak>.01f){lastSignal=SystemClock.elapsedRealtime();silentRetries=0;}
                    if (!signalReported && data.peak > .01f) { signalReported = true; Log.i("FrontRecordAudio", "output_mix_signal_detected"); }
                }
                public void onFftDataCapture(Visualizer v, byte[] samples, int rate) { data.sampling(rate,samples.length);data.fft(samples); }
            }, Math.min(20000, Visualizer.getMaxCaptureRate()), true, true);
            if (result != Visualizer.SUCCESS || candidate.setEnabled(true) != Visualizer.SUCCESS) throw new IllegalStateException("capture start");
            started=SystemClock.elapsedRealtime();failed=false;failures=0; status = ""; Log.i("FrontRecordAudio", "output_mix_connected");
        } catch (RuntimeException e) { stop(); failed = true;failures++;retryAt=SystemClock.elapsedRealtime()+2000; status = "오디오 분석 연결 대기 · 설정에서 재시도"; Log.w("FrontRecordAudio", "output_mix_unavailable: " + e.getClass().getSimpleName()); }
    }
    void stop() { if (visualizer != null) { try { visualizer.release(); } catch (RuntimeException ignored) {} visualizer = null; Log.i("FrontRecordAudio", "output_mix_released"); } data.clear(); }
    void retry() { stop();failed = false;failures=silentRetries=0;retryAt=0;start(); }
    boolean connected() { return visualizer != null; }
    String status() { return connected()&&SystemClock.elapsedRealtime()-Math.max(started,lastSignal)>1500?"소리 신호를 받지 못하고 있어요":status; }
}
