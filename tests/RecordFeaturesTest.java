package dev.tossfront.record;

import java.util.Calendar;
import java.util.TimeZone;

public final class RecordFeaturesTest {
    private static int checks;
    private static void check(boolean pass,String label){checks++;if(!pass)throw new AssertionError(label);}
    private static boolean near(float a,float b){return Math.abs(a-b)<.0001f;}
    public static void main(String[] args){
        TimerPlan p=new TimerPlan();long now=1000000;
        p.duration(now,1,"first");check(p.remaining(now)==60000,"one minute");
        check(!p.consume("first",now+59999),"early alarm cannot stop");
        p.duration(now+100,30,"replacement");
        check(!p.consume("first",now+60000),"stale alarm cannot consume replacement");
        check(p.kind==TimerPlan.DEADLINE,"replacement remains armed");
        check(p.consume("replacement",p.deadline),"matching deadline fires");
        check(!p.consume("replacement",Long.MAX_VALUE),"alarm is one shot");
        p.duration(now,15,"cancelled");p.clear();
        check(!p.consume("cancelled",Long.MAX_VALUE),"cancel prevents playback stop");
        p.kind=TimerPlan.TRACK_END;p.token="end";
        check(!p.consume("end",Long.MAX_VALUE),"deadline cannot consume end-of-track");
        check(p.trackEnded()&&!p.trackEnded(),"track end is one shot");
        p.duration(now,30,"deadline");check(!p.trackEnded(),"track event does not stop deadline timer");
        TimeZone zone=TimeZone.getTimeZone("Asia/Seoul");Calendar c=Calendar.getInstance(zone);
        c.clear();c.set(2026,Calendar.OCTOBER,9,23,50,0);long evening=c.getTimeInMillis();
        p.at(evening,0,10,zone,"clock");check(p.remaining(evening)==20*60000,"clock wraps midnight");
        p.at(evening,23,55,zone,"clock");check(p.remaining(evening)==5*60000,"clock later today");
        p.at(evening,23,50,zone,"clock");check(p.remaining(evening)==24*60*60000L,"same minute schedules tomorrow");
        p.duration(now,0,"clamped");check(p.remaining(now)==60000,"minimum duration");
        p.duration(now,900,"clamped");check(p.remaining(now)==720*60000L,"maximum duration");
        check(ResponseMath.percent(0)==25&&ResponseMath.percent(999)==420,"sensitivity bounds");
        check(near(ResponseMath.signed(.2f,4.2f),ResponseMath.signed(.2f,1.4f)*3),"new maximum is three times old gain");
        check(near(ResponseMath.band(.4f,4.2f),1.68f),"bands retain energy above one");
        check(ResponseMath.signed(1,4.2f)==3&&ResponseMath.signed(-1,4.2f)==-3,"signed output bounded");
        check(ResponseMath.band(-.5f,4.2f)==0&&ResponseMath.band(1,4.2f)==3,"band bounds");
        check(ResponseMath.display(3)>ResponseMath.display(1)&&ResponseMath.display(3)<1.65f,"strong response fits screen");
        check(near(ResponseMath.rms(new float[]{.5f,-.5f}),.5f)&&ResponseMath.rms(new float[0])==0,"actual waveform RMS");
        SpectrumData spectrum=new SpectrumData();spectrum.sampling(44100000,1024);
        byte[] low=new byte[1024];low[6]=100;spectrum.fft(low);
        check(spectrum.lowVocal>0&&spectrum.highVocal==0,"129 Hz routes to low vocal range");
        byte[] high=new byte[1024];high[20]=100;spectrum.fft(high);
        check(spectrum.highVocal>0&&spectrum.lowVocal==0,"431 Hz routes to high vocal range");
        spectrum.clear();check(spectrum.lowVocal==0&&spectrum.highVocal==0,"capture release clears vocal ranges");
        System.out.println(checks+" timer and sensitivity checks passed");
    }
}
