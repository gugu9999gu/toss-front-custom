package dev.tossfront.record;

import android.app.Dialog;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.*;
import android.widget.*;
import java.util.Calendar;

final class TimerSheet {
    private final Context context;private final Appearance a;private final Dialog dialog;
    private final PlaybackTimer timer;private final Handler handler=new Handler();
    private final Runnable changed;private TextView state;private int minutes=30;
    private final Runnable tick=new Runnable(){public void run(){if(!dialog.isShowing())return;state.setText(timer.label());handler.postDelayed(this,1000);}};
    TimerSheet(Context c,Appearance a,boolean overlay,Runnable changed,Runnable closed){context=c;this.a=a;this.changed=changed;timer=PlaybackTimer.get(c);dialog=new Dialog(c,android.R.style.Theme_Material_Light_NoActionBar);dialog.setOnDismissListener(d->{handler.removeCallbacks(tick);closed.run();});if(overlay)dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);}
    void dismiss(){dialog.dismiss();}
    private int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size){TextView v=new TextView(context);v.setText(value);v.setTextSize(size);v.setTextColor(a.text());v.setPadding(0,dp(6),0,dp(6));return v;}
    private TextView button(String label,Runnable action){TextView v=text(label,14);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(48));GradientDrawable bg=new GradientDrawable();bg.setColor(a.bg());bg.setCornerRadius(dp(12));v.setBackground(bg);v.setOnClickListener(x->action.run());RecordPanel.press(v,a);return v;}
    private void update(){state.setText(timer.label());changed.run();}
    void show(){LinearLayout outer=new LinearLayout(context);outer.setOrientation(1);outer.setPadding(dp(24),dp(12),dp(24),dp(16));GradientDrawable bg=new GradientDrawable();bg.setColor(a.surface());bg.setCornerRadius(dp(28));outer.setBackground(bg);
        LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(text("재생 정지 예약",18),new LinearLayout.LayoutParams(0,dp(48),1));header.addView(button("닫기",dialog::dismiss),new LinearLayout.LayoutParams(dp(56),dp(48)));outer.addView(header);
        ScrollView scroll=new ScrollView(context);LinearLayout body=new LinearLayout(context);body.setOrientation(1);scroll.addView(body);outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));state=text(timer.label(),16);state.setTextColor(a.accent());body.addView(state);body.addView(text("예약이 끝나면 음악 재생만 멈춥니다.",13));
        body.addView(text("시간이 지난 후",14));LinearLayout presets=new LinearLayout(context);for(int value:new int[]{15,30,60,90}){TextView b=button(value+"분",()->{timer.duration(value);update();});presets.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));}body.addView(presets);
        TextView duration=text("직접 선택 · "+minutes+"분",14);body.addView(duration);SeekBar bar=new SeekBar(context);bar.setMax(179);bar.setProgress(minutes-1);bar.setContentDescription("재생 정지까지의 분");bar.setProgressTintList(ColorStateList.valueOf(a.accent()));bar.setThumbTintList(ColorStateList.valueOf(a.accent()));bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int v,boolean user){if(user){minutes=v+1;duration.setText("직접 선택 · "+minutes+"분");}}public void onStopTrackingTouch(SeekBar b){}});body.addView(bar,new LinearLayout.LayoutParams(-1,dp(48)));body.addView(button("선택한 시간으로 예약",()->{timer.duration(minutes);update();}));
        body.addView(text("지정한 시각에",14));Context pickerContext=new ContextThemeWrapper(context,a.dark()?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);int pickerStyle=context.getResources().getIdentifier("RecordTimePicker","style",context.getPackageName());TimePicker picker=new TimePicker(pickerContext,null,0,pickerStyle);picker.setIs24HourView(true);Calendar now=Calendar.getInstance();now.add(Calendar.MINUTE,30);picker.setHour(now.get(Calendar.HOUR_OF_DAY));picker.setMinute(now.get(Calendar.MINUTE));body.addView(picker,new LinearLayout.LayoutParams(-1,dp(130)));body.addView(button("선택한 시각에 정지",()->{timer.at(picker.getHour(),picker.getMinute());update();}));
        TextView end=button("이번 곡이 끝나면 정지",()->{timer.afterTrack();update();});end.setEnabled(SessionRepository.get(context).connected());end.setAlpha(end.isEnabled()?1:.4f);body.addView(end);body.addView(button("예약 취소",()->{timer.cancel();update();}));
        if(!timer.exactAllowed()){body.addView(text("정확한 예약 권한이 없으면 정지 시각이 늦어질 수 있습니다.",12));body.addView(button("정확한 예약 허용",()->context.startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+context.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))));}
        dialog.setContentView(outer,new ViewGroup.LayoutParams(-1,Math.round(context.getResources().getDisplayMetrics().heightPixels*.8f)));Window w=dialog.getWindow();w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.BOTTOM);dialog.show();w.setLayout(-1,-2);handler.post(tick);
    }
}
