package dev.tossfront.record;

import android.app.*;
import android.content.Intent;
import android.os.*;

/** Only pauses the selected web session. It never launches an app or sends global media keys. */
public final class TimerStopService extends Service implements SessionRepository.Observer {
    private final Handler handler=new Handler(Looper.getMainLooper());private SessionRepository repository;private boolean done;
    @Override public void onCreate(){super.onCreate();NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);manager.createNotificationChannel(new NotificationChannel("timer_stop","재생 정지 예약",NotificationManager.IMPORTANCE_LOW));startForeground(402,new Notification.Builder(this,"timer_stop").setSmallIcon(getResources().getIdentifier("ic_notification","drawable",getPackageName())).setContentTitle("예약한 음악 재생을 멈추는 중").build());repository=SessionRepository.get(this);repository.observe(this);handler.postDelayed(this::stopSelf,6000);}
    @Override public void changed(){if(done||!repository.connected())return;done=true;repository.pause();handler.postDelayed(this::stopSelf,500);}
    @Override public int onStartCommand(Intent intent,int flags,int id){return START_NOT_STICKY;}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(repository!=null)repository.remove(this);stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
