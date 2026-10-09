package dev.tossfront.record;

/** Low-quality visualization samples stay in these bounded in-memory arrays. */
final class SpectrumData {
    final float[] waveform = new float[128], bands = new float[64];
    long sequence;
    float peak;
    float lowVocal,highVocal;private int samplingHz=44100,captureSize=512;
    void sampling(int milliHertz,int size){if(milliHertz>0)samplingHz=milliHertz/1000;if(size>0)captureSize=size;}
    private float range(byte[] data,int low,int high){int first=Math.max(1,(int)Math.ceil(low*(double)captureSize/samplingHz)),last=Math.min(data.length/2-1,(int)Math.floor(high*(double)captureSize/samplingHz));double sum=0;int count=0;for(int bin=first;bin<=last;bin++){double value=Math.hypot(data[bin*2],data[bin*2+1])/181.;sum+=value*value;count++;}return count==0?0:(float)Math.min(1,Math.log1p(Math.sqrt(sum/count)*12)/Math.log(13));}
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
        lowVocal=range(data,80,220);highVocal=range(data,220,600);
        int max = data.length / 2 - 1;
        for (int i = 0; i < bands.length; i++) {
            int first = Math.max(1, (int)Math.floor(Math.pow(max, i / (double)bands.length)));
            int last = Math.min(max + 1, Math.max(first + 1, (int)Math.ceil(Math.pow(max, (i + 1) / (double)bands.length))));
            double energy = 0;
            for (int k = first; k < last; k++) energy = Math.max(energy, Math.hypot(data[k * 2], data[k * 2 + 1]) / 181.0);
            bands[i] = (float)Math.min(1, Math.log1p(energy * 12) / Math.log(13));
        }
    }
    void clear() { java.util.Arrays.fill(waveform, 0); java.util.Arrays.fill(bands, 0); peak = lowVocal = highVocal = 0; }
}
