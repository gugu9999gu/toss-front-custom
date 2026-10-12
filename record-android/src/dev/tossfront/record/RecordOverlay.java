package dev.tossfront.record;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/** A temporary playback surface. System navigation must reveal its destination. */
public final class RecordOverlay extends Service implements RecordPanel.Host {
    private static RecordOverlay current;
    static boolean showing(){return current!=null&&!current.closing&&current.panel!=null;}
    static void dismissForNavigation(){if(current!=null)current.finishOverlay(null);}
    private android.widget.FrameLayout sceneLayer;
    private WindowManager windows;
    private RecordPanel panel;
    private boolean full,autoSkip,closing,receiverRegistered;
    private int background;
    private BackHandler backHandler;
    private static final class BackHandler {
        private final OnBackInvokedDispatcher dispatcher;
        private final OnBackInvokedCallback callback;
        BackHandler(RecordPanel panel){dispatcher=panel.findOnBackInvokedDispatcher();callback=panel::back;if(dispatcher!=null)dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback);}
        void close(){if(dispatcher!=null)dispatcher.unregisterOnBackInvokedCallback(callback);}
    }
    private final BroadcastReceiver navigation=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())||Intent.ACTION_SHUTDOWN.equals(intent.getAction()))finishOverlay(null);
            else if(Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())){
                String reason=intent.getStringExtra("reason");
                if("homekey".equals(reason)||"recentapps".equals(reason)||"gestureNav".equals(reason)||"assist".equals(reason)||"globalactions".equals(reason))finishOverlay(null);
            }
        }
    };
    @Override public void onCreate(){
        super.onCreate();current=this;
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel("record","레코드 화면",NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,RecordOverlay.class).setAction("close"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,"record").setSmallIcon(getResources().getIdentifier("ic_notification","drawable",getPackageName())).setContentTitle("재생 화면 표시 중").setContentText("알림을 눌러 재생 화면을 닫을 수 있습니다.").setContentIntent(stop).setOngoing(true).addAction(new Notification.Action.Builder(null,"닫기",stop).build()).build();
        startForeground(1,notification);
        IntentFilter filter=new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SHUTDOWN);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(navigation,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(navigation,filter);
        receiverRegistered=true;
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(closing)return START_NOT_STICKY;
        if(intent==null){finishOverlay(null);return START_NOT_STICKY;}
        if("close".equals(intent.getAction())){if(panel==null)finishOverlay(null);else close();return START_NOT_STICKY;}
        if(!Settings.canDrawOverlays(this)){finishOverlay(null);return START_NOT_STICKY;}
        if(panel==null){
            windows=(WindowManager)getSystemService(WINDOW_SERVICE);
            Appearance a=new Appearance(this);full=a.immersive;background=a.bg();autoSkip=a.autoSkip;
            try{
                sceneLayer=new android.widget.FrameLayout(this);sceneLayer.setBackgroundColor(background);windows.addView(sceneLayer,window(true));
                panel=new RecordPanel(this,this,true,sceneLayer);panel.setFocusableInTouchMode(true);windows.addView(panel,window(false));panel.requestFocus();
                panel.post(()->{
                    if(closing||panel==null)return;
                    if(Build.VERSION.SDK_INT>=33)backHandler=new BackHandler(panel);
                    applyBars();
                });
                SessionRepository.get(this).openWeb(this,true);
            }catch(RuntimeException failure){finishOverlay(null);}
        }
        return START_NOT_STICKY;
    }
    private WindowManager.LayoutParams window(boolean scene){
        int flags=WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        flags|=scene?WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE:WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,flags,scene?PixelFormat.OPAQUE:PixelFormat.TRANSLUCENT);
        p.setTitle(scene?"FrontRecord scene":"FrontRecord playback");p.gravity=Gravity.TOP;
        if(Build.VERSION.SDK_INT>=30)p.setFitInsetsTypes(0);
        if(Build.VERSION.SDK_INT>=28)p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        return p;
    }
    private void applyBars(){
        if(panel==null||closing)return;
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController control=panel.getWindowInsetsController();if(control!=null){control.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);if(full)control.hide(WindowInsets.Type.systemBars());else control.show(WindowInsets.Type.systemBars());}}
    }
    public void appearanceChanged(){
        if(closing||panel==null)return;
        Appearance a=new Appearance(this);
        if(full!=a.immersive||background!=a.bg()||autoSkip!=a.autoSkip){full=a.immersive;background=a.bg();autoSkip=a.autoSkip;sceneLayer.setBackgroundColor(background);applyBars();SessionRepository.get(this).openWeb(this,true);}
    }
    private void detachViews(){
        if(current==this)current=null;
        if(backHandler!=null){backHandler.close();backHandler=null;}
        RecordPanel oldPanel=panel;panel=null;
        if(oldPanel!=null){oldPanel.prepareClose();try{windows.removeViewImmediate(oldPanel);}catch(IllegalArgumentException alreadyRemoved){}}
        android.widget.FrameLayout oldScene=sceneLayer;sceneLayer=null;
        if(oldScene!=null)try{windows.removeViewImmediate(oldScene);}catch(IllegalArgumentException alreadyRemoved){}
    }
    private void finishOverlay(Runnable destination){
        if(closing)return;
        closing=true;
        detachViews();
        if(receiverRegistered){unregisterReceiver(navigation);receiverRegistered=false;}
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
        // Home/Recents/screen-off never bring YouTube back over the chosen destination.
        if(destination!=null)destination.run();
    }
    public void openSource(){finishOverlay(()->SessionRepository.get(this).openWeb(this,false));}
    public void openAudio(){finishOverlay(()->{SessionRepository.get(this).openWeb(this,false);Intent intent=getPackageManager().getLaunchIntentForPackage("dev.tossfront.audio");if(intent!=null)startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));});}
    public void audioPermission(){finishOverlay(()->startActivity(new Intent(this,MainActivity.class).putExtra("ask_audio",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));}
    public void cameraPermission(){finishOverlay(()->startActivity(new Intent(this,MainActivity.class).putExtra("ask_camera",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));}
    public void showRecord(){}
    public void connectionSettings(){finishOverlay(()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));}
    public void close(){openSource();}
    @Override public void onTaskRemoved(Intent root){finishOverlay(null);super.onTaskRemoved(root);}
    @Override public void onDestroy(){closing=true;detachViews();if(receiverRegistered){unregisterReceiver(navigation);receiverRegistered=false;}stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
