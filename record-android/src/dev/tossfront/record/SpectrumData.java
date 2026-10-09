package dev.tossfront.record;

/** Low-quality visualization samples stay in these bounded in-memory arrays. */
final class SpectrumData {
    final float[] waveform = new float[128], bands = new float[64];
    long sequence;
    float peak;
    void waveform(byte[] data) {
        peak = 0;
        for (int i = 0; i < waveform.length; i++) {
            int first = i * data.length / waveform.length, last = Math.max(first + 1, (i + 1) * data.length / waveform.length);
            float sum = 0;
            for (int j = first; j < last && j < data.length; j++) sum += ((data[j] & 255) - 128) / 128f;
            waveform[i] = sum / (last - first); peak = Math.max(peak, Math.abs(waveform[i]));
        }
        sequence++;
    }
    void fft(byte[] data) {
        int max = data.length / 2 - 1;
        for (int i = 0; i < bands.length; i++) {
            int first = Math.max(1, (int)Math.floor(Math.pow(max, i / (double)bands.length)));
            int last = Math.min(max + 1, Math.max(first + 1, (int)Math.ceil(Math.pow(max, (i + 1) / (double)bands.length))));
            double energy = 0;
            for (int k = first; k < last; k++) energy = Math.max(energy, Math.hypot(data[k * 2], data[k * 2 + 1]) / 181.0);
            bands[i] = (float)Math.min(1, Math.log1p(energy * 12) / Math.log(13));
        }
    }
    void clear() { java.util.Arrays.fill(waveform, 0); java.util.Arrays.fill(bands, 0); peak = 0; }
}
