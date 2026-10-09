package dev.tossfront.record;

import android.content.*;

public final class TimerReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){PlaybackTimer timer=PlaybackTimer.get(context);if("dev.tossfront.record.STOP_TIMER".equals(intent.getAction())){if(timer.consume(intent.getStringExtra("token")))context.startForegroundService(new Intent(context,TimerStopService.class));}else timer.reschedule();}
}
