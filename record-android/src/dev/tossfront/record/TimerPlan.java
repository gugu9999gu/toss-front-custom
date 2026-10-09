package dev.tossfront.record;

import java.util.Calendar;
import java.util.TimeZone;

/** Pure, one-shot stop plan. An old alarm cannot consume a replacement plan. */
final class TimerPlan {
    static final int NONE=0,DEADLINE=1,TRACK_END=2;
    int kind;long deadline;String token="";
    void duration(long now,int minutes,String next){kind=DEADLINE;deadline=now+Math.max(1,Math.min(720,minutes))*60000L;token=next;}
    void at(long now,int hour,int minute,TimeZone zone,String next){Calendar c=Calendar.getInstance(zone);c.setTimeInMillis(now);c.set(Calendar.HOUR_OF_DAY,Math.max(0,Math.min(23,hour)));c.set(Calendar.MINUTE,Math.max(0,Math.min(59,minute)));c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);if(c.getTimeInMillis()<=now)c.add(Calendar.DATE,1);kind=DEADLINE;deadline=c.getTimeInMillis();token=next;}
    boolean consume(String value,long now){if(kind!=DEADLINE||!token.equals(value)||now<deadline)return false;clear();return true;}
    boolean trackEnded(){if(kind!=TRACK_END)return false;clear();return true;}
    void clear(){kind=NONE;deadline=0;token="";}
    long remaining(long now){return kind==DEADLINE?Math.max(0,deadline-now):0;}
}
