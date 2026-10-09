package dev.tossfront.record;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.util.*;

final class PlaybackTimer {
    private static PlaybackTimer instance;
    private final Context context;private final android.content.SharedPreferences prefs;
    final TimerPlan plan=new TimerPlan();
    private PlaybackTimer(Context c){context=c.getApplicationContext();prefs=context.getSharedPreferences("playback_timer",0);reload();}
    static synchronized PlaybackTimer get(Context c){if(instance==null)instance=new PlaybackTimer(c);return instance;}
    void reload(){plan.kind=prefs.getInt("kind",0);plan.deadline=prefs.getLong("deadline",0);plan.token=prefs.getString("token","");}
    private PendingIntent alarm(){return PendingIntent.getBroadcast(context,401,new Intent(context,TimerReceiver.class).setAction("dev.tossfront.record.STOP_TIMER").putExtra("token",plan.token),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private void save(){prefs.edit().putInt("kind",plan.kind).putLong("deadline",plan.deadline).putString("token",plan.token).commit();}
    boolean exactAllowed(){return Build.VERSION.SDK_INT<31||((AlarmManager)context.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();}
    void duration(int minutes){cancel();plan.duration(System.currentTimeMillis(),minutes,UUID.randomUUID().toString());save();reschedule();}
    void at(int hour,int minute){cancel();plan.at(System.currentTimeMillis(),hour,minute,TimeZone.getDefault(),UUID.randomUUID().toString());save();reschedule();}
    void afterTrack(){cancel();plan.kind=TimerPlan.TRACK_END;plan.token=UUID.randomUUID().toString();save();}
    void cancel(){((AlarmManager)context.getSystemService(Context.ALARM_SERVICE)).cancel(alarm());plan.clear();save();}
    void reschedule(){reload();if(plan.kind!=TimerPlan.DEADLINE)return;AlarmManager manager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);long due=Math.max(System.currentTimeMillis()+100,plan.deadline);if(exactAllowed())manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,alarm());else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,alarm());}
    boolean consume(String token){reload();boolean done=plan.consume(token,System.currentTimeMillis());if(done)save();return done;}
    boolean trackEnded(){boolean done=plan.trackEnded();if(done)save();return done;}
    String label(){if(plan.kind==TimerPlan.TRACK_END)return "이번 곡이 끝나면 재생 정지";if(plan.kind!=TimerPlan.DEADLINE)return "예약 없음";long seconds=(plan.remaining(System.currentTimeMillis())+999)/1000;return "재생 정지까지 "+String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
}
