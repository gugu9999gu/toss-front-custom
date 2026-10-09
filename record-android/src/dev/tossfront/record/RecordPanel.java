package dev.tossfront.record;

import android.animation.ValueAnimator;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.session.PlaybackState;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.*;

public final class RecordPanel extends ScrollView implements SessionRepository.Observer {
    public interface Host { void openSource(); void openAudio(); void showRecord(); void connectionSettings(); void audioPermission(); void appearanceChanged(); void close(); }
    private static final PathInterpolator EASE=new PathInterpolator(.22f,1,.36f,1);
    private final SessionRepository repository;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Appearance appearance;
    private final VisualStage stage;
    private final TextView status,title,artist,elapsed,total,hint,connection;
    private final Control previous,toggle,next,loop,history;
    private final SeekBar seek;
    private final LinearLayout body;
    private final LinearLayout heading,time,controls;
    private final Host host;
    private final boolean overlay;
    private TextView floating;
    private boolean attached,dragging,closing,entered;
    private int ticks;
    private int sheets;
    private AppearanceSheet settingsSheet;
    private HistorySheet historySheet;
    private boolean headerHidden,waking;
    private final Runnable hideHeader=new Runnable(){public void run(){if(!attached||sheets>0||getWindowVisibility()!=VISIBLE)return;headerHidden=true;heading.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);if(appearance.motion())heading.animate().alpha(0).setDuration(220).start();else heading.setAlpha(0);}};
    private ValueAnimator palette;
    private final int[] colors=new int[6];
    private final Runnable clock=new Runnable(){public void run(){if(!attached||getWindowVisibility()!=VISIBLE)return;stage.refresh();updateTime();updateStatus();if(++ticks%20==0)repository.connect();main.postDelayed(this,100);}};

    public RecordPanel(Context context,Host host,boolean overlay) {
        super(context);this.host=host;this.overlay=overlay;appearance=new Appearance(context);repository=SessionRepository.get(context);setFillViewport(true);setVerticalScrollBarEnabled(false);setClipToPadding(false);
        if(overlay)setOnApplyWindowInsetsListener((view,insets)->{if(appearance.immersive)view.setPadding(0,0,0,0);else view.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(8),dp(24),dp(16));addView(body);
        heading=new LinearLayout(context);heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(new Space(context),new LinearLayout.LayoutParams(0,dp(48),1));history=new Control(context,4,appearance);history.setContentDescription("재생 기록 · 즐겨찾기");history.setOnClickListener(v->showHistory());heading.addView(history,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView options=textButton("•••",this::showSettings);options.setTextSize(18);options.setContentDescription("화면 설정");heading.addView(options,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView close=textButton(overlay?"×":"홈",host::close);close.setContentDescription(overlay?"레코드 화면 닫기":"홈으로");close.setTextSize(overlay?24:13);heading.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));body.addView(heading);
        if(!overlay){floating=textButton("재생 화면 열기",host::showRecord);add(body,floating,4);}
        stage=new VisualStage(context);int screen=(int)(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density);int height=Math.min(295,Math.max(210,screen-(overlay?390:438)));
        LinearLayout.LayoutParams stageParams=new LinearLayout.LayoutParams(-1,dp(height));stageParams.topMargin=dp(4);body.addView(stage,stageParams);stage.setOnLongClickListener(v->{showSettings();return true;});
        status=label("",12);status.setGravity(Gravity.CENTER);status.setMinHeight(dp(18));add(body,status,0);
        title=label("재생 중인 곡이 없어요",22);title.setTypeface(Typeface.create("sans-serif-medium",0));title.setMinLines(2);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);add(body,title,12);
        artist=label("YouTube 웹에서 영상을 재생해 주세요.",14);artist.setSingleLine(true);artist.setEllipsize(TextUtils.TruncateAt.END);add(body,artist,4);
        seek=new SeekBar(context);seek.setMax(10000);seek.setContentDescription("재생 위치");LinearLayout.LayoutParams seekParams=new LinearLayout.LayoutParams(-1,dp(48));seekParams.topMargin=dp(6);body.addView(seek,seekParams);
        time=new LinearLayout(context);elapsed=label("0:00",13);total=label("—",13);elapsed.setFontFeatureSettings("tnum");total.setFontFeatureSettings("tnum");total.setGravity(Gravity.END);time.addView(elapsed,new LinearLayout.LayoutParams(0,-2,1));time.addView(total,new LinearLayout.LayoutParams(0,-2,1));body.addView(time);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){dragging=true;}public void onProgressChanged(SeekBar bar,int value,boolean user){if(user&&repository.duration()>0)elapsed.setText(format(repository.duration()*value/bar.getMax()));}public void onStopTrackingTouch(SeekBar bar){repository.seek(repository.duration()*bar.getProgress()/bar.getMax());dragging=false;}});
        controls=new LinearLayout(context);controls.setGravity(Gravity.CENTER);previous=new Control(context,0,appearance);toggle=new Control(context,1,appearance);next=new Control(context,3,appearance);loop=new Control(context,5,appearance);
        previous.setOnClickListener(v->repository.previous());toggle.setOnClickListener(v->repository.toggle());next.setOnClickListener(v->repository.next());loop.setOnClickListener(v->repository.cycleLoop());controls.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(64)));LinearLayout.LayoutParams center=new LinearLayout.LayoutParams(dp(72),dp(72));center.setMargins(dp(12),0,dp(12),0);controls.addView(toggle,center);controls.addView(next,new LinearLayout.LayoutParams(dp(48),dp(64)));controls.addView(loop,new LinearLayout.LayoutParams(dp(48),dp(64)));add(body,controls,14);
        hint=label("",12);hint.setGravity(Gravity.CENTER);add(body,hint,10);
        connection=textButton("재생 정보 연결 설정",host::connectionSettings);add(body,connection,8);
        setPalette(new int[]{appearance.bg(),appearance.surface(),appearance.text(),appearance.muted(),appearance.track(),appearance.accent()});stage.applyAppearance(appearance);
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size){TextView v=new TextView(getContext());v.setText(text);v.setTextSize(size);v.setTextColor(appearance.text());return v;}
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
    private void add(LinearLayout parent,View view,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(view,p);}
    private TextView textButton(String text,Runnable action){TextView v=label(text,13);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(48));v.setPadding(dp(8),dp(8),dp(8),dp(8));v.setClickable(true);v.setFocusable(true);v.setOnClickListener(x->action.run());press(v,appearance);return v;}
    static void press(View view,Appearance a){view.setOnTouchListener((v,event)->{if(!v.isEnabled())return false;int action=event.getAction();if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){float scale=action==MotionEvent.ACTION_DOWN?.97f:1;if(a.motion())v.animate().scaleX(scale).scaleY(scale).setDuration(110).setInterpolator(EASE).start();else{v.setScaleX(scale);v.setScaleY(scale);}}return false;});}
    private void revealHeader(){headerHidden=false;heading.animate().cancel();heading.setAlpha(1);heading.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);main.removeCallbacks(hideHeader);if(attached&&sheets==0)main.postDelayed(hideHeader,5000);}
    @Override public boolean dispatchTouchEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_DOWN){waking=headerHidden;revealHeader();}if(waking){if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL)waking=false;return true;}return super.dispatchTouchEvent(event);}
    private void sheetClosed(){sheets=Math.max(0,sheets-1);revealHeader();}
    private void dismissSheets(){if(settingsSheet!=null){settingsSheet.dismiss();settingsSheet=null;}if(historySheet!=null){historySheet.dismiss();historySheet=null;}}
    private void showSettings(){dismissSheets();sheets++;revealHeader();settingsSheet=new AppearanceSheet(getContext(),appearance,host,this::applyAppearance,()->{stage.audio.retry();changed();},this::sheetClosed,overlay);settingsSheet.show();}
    private void showHistory(){dismissSheets();sheets++;revealHeader();historySheet=new HistorySheet(getContext(),appearance,overlay,()->{if(!overlay)host.showRecord();},this::sheetClosed);historySheet.show();}
    public void preferencesChanged(){appearance.reload();applyAppearance();}
    private void applyAppearance(){stage.applyAppearance(appearance);host.appearanceChanged();requestApplyInsets();int[] target={appearance.bg(),appearance.surface(),appearance.text(),appearance.muted(),appearance.track(),appearance.accent()};if(palette!=null)palette.cancel();if(appearance.motion()){int[] from=colors.clone();palette=ValueAnimator.ofFloat(0,1);palette.setDuration(200);palette.setInterpolator(EASE);palette.addUpdateListener(v->{float f=(float)v.getAnimatedValue();int[] mixed=new int[6];for(int i=0;i<6;i++)mixed[i]=blend(from[i],target[i],f);setPalette(mixed);});palette.start();}else setPalette(target);changed();}
    private int blend(int a,int b,float t){return Color.rgb(Math.round(Color.red(a)+(Color.red(b)-Color.red(a))*t),Math.round(Color.green(a)+(Color.green(b)-Color.green(a))*t),Math.round(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}
    private void setPalette(int[] value){System.arraycopy(value,0,colors,0,6);setBackgroundColor(colors[0]);for(TextView v:new TextView[]{title,artist,elapsed,total,status,hint,connection})if(v!=null)v.setTextColor(v==title?colors[2]:colors[3]);seek.setProgressTintList(ColorStateList.valueOf(colors[5]));seek.setProgressBackgroundTintList(ColorStateList.valueOf(colors[4]));seek.setThumbTintList(ColorStateList.valueOf(colors[5]));for(Control control:new Control[]{previous,toggle,next,history,loop})control.colors(colors[5],colors[0],colors[2]);if(floating!=null){floating.setTextColor(colors[2]);floating.setBackground(shape(colors[1]));}for(int i=0;i<heading.getChildCount();i++){View v=heading.getChildAt(i);if(v instanceof TextView)((TextView)v).setTextColor(colors[2]);}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;closing=false;setAlpha(1);setTranslationY(0);repository.observe(this);main.post(clock);revealHeader();if(!entered&&appearance.motion()){entered=true;body.setAlpha(0);body.setTranslationY(dp(6));body.animate().alpha(1).translationY(0).setDuration(220).setInterpolator(EASE).start();}}
    @Override protected void onDetachedFromWindow(){attached=false;dismissSheets();main.removeCallbacks(clock);main.removeCallbacks(hideHeader);heading.animate().cancel();repository.remove(this);animate().cancel();body.animate().cancel();if(palette!=null)palette.cancel();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(attached){main.removeCallbacks(clock);if(visibility==VISIBLE)main.post(clock);}}
    public void dismiss(Runnable finish){if(closing)return;closing=true;if(appearance.motion())animate().alpha(0).translationY(dp(4)).setDuration(130).setInterpolator(EASE).withEndAction(finish).start();else finish.run();}
    private void updateLabel(TextView view,String value,boolean motion){if(TextUtils.equals(view.getText(),value))return;view.animate().cancel();view.setText(value);if(motion&&appearance.motion()){view.setAlpha(.6f);view.setTranslationY(dp(2));view.animate().alpha(1).translationY(0).setDuration(160).setInterpolator(EASE).start();}else{view.setAlpha(1);view.setTranslationY(0);}}
    @Override public void changed(){updateLabel(title,repository.title(),true);updateLabel(artist,repository.artist(),true);stage.update(repository.playing(),repository.artwork());toggle.setMode(repository.playing()?2:1);toggle.setContentDescription(repository.playing()?"일시정지":"재생");toggle.setEnabled(repository.supports(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE));title.setVisibility(appearance.showTitle?VISIBLE:GONE);artist.setVisibility(appearance.showTitle?VISIBLE:GONE);seek.setVisibility(appearance.showProgress?VISIBLE:GONE);time.setVisibility(appearance.showProgress?VISIBLE:GONE);toggle.setVisibility(appearance.showPlay?VISIBLE:GONE);previous.setVisibility(appearance.showSkip&&repository.canSkip(-1)?VISIBLE:GONE);next.setVisibility(appearance.showSkip&&repository.canSkip(1)?VISIBLE:GONE);loop.setVisibility(appearance.showLoop?VISIBLE:GONE);loop.setMode(5+repository.loopMode());loop.setContentDescription(repository.loopMode()==0?"반복 꺼짐":repository.loopMode()==1?"한 곡 반복":"즐겨찾기 목록 반복");controls.setVisibility(appearance.showPlay||appearance.showLoop||previous.getVisibility()==VISIBLE||next.getVisibility()==VISIBLE?VISIBLE:GONE);seek.setEnabled(repository.supports(PlaybackState.ACTION_SEEK_TO)&&repository.duration()>0);connection.setVisibility(repository.hasAccess()?GONE:VISIBLE);updateLabel(hint,!overlay?"웹에서 재생 후 ‘재생 화면 열기’를 눌러 주세요.":"",false);hint.setVisibility(overlay?GONE:VISIBLE);setKeepScreenOn(repository.playing());resizeStage();updateTime();updateStatus();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);if(stage!=null)resizeStage();}
    private void resizeStage(){float density=getResources().getDisplayMetrics().density;int screen=Math.round((getHeight()>0?getHeight()-getPaddingTop()-getPaddingBottom():getResources().getDisplayMetrics().heightPixels)/density);int available=Math.max(120,screen-56-18-16-(overlay?0:76)-(appearance.showTitle?104:0)-(appearance.showProgress?78:0)-(controls.getVisibility()==VISIBLE?90:0));int height=dp(Math.max(90,Math.round(available*appearance.area/100f)));ViewGroup.LayoutParams params=stage.getLayoutParams();if(params.height!=height){params.height=height;stage.setLayoutParams(params);}}
    private void updateStatus(){String value=repository.error()?"웹 화면에서 재생 오류를 확인해 주세요":repository.buffering()?"불러오는 중":stage.analysisStatus();updateLabel(status,value,false);}
    private void updateTime(){if(!dragging){long position=repository.position(),duration=repository.duration();updateLabel(elapsed,format(position),false);updateLabel(total,duration>0?format(duration):"—",false);int progress=duration>0?(int)(position*seek.getMax()/duration):0;seek.setProgress(progress,repository.playing()&&appearance.motion()&&Math.abs(progress-seek.getProgress())<200);}}
    static String format(long ms){long seconds=Math.max(0,ms/1000);return seconds>=3600?String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60):String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}

    static final class Control extends View {
        int mode,oldMode,accent,paper,ink;private float mix=1;private ValueAnimator icon;private final Appearance a;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path triangle=new Path();
        Control(Context c,int mode,Appearance a){super(c);this.mode=mode;this.a=a;setClickable(true);setFocusable(true);setContentDescription(mode==0?"이전 영상":mode==3?"다음 영상":"재생");press(this,a);}
        void colors(int accent,int paper,int ink){this.accent=accent;this.paper=paper;this.ink=ink;invalidate();}
        void setMode(int value){if(mode==value)return;if(icon!=null)icon.cancel();oldMode=mode;mode=value;if(!a.motion()){mix=1;invalidate();return;}mix=0;icon=ValueAnimator.ofFloat(0,1);icon.setDuration(160);icon.setInterpolator(EASE);icon.addUpdateListener(v->{mix=(float)v.getAnimatedValue();invalidate();});icon.start();}
        @Override public void setEnabled(boolean enabled){if(isEnabled()==enabled)return;super.setEnabled(enabled);if(a.motion())animate().alpha(enabled?1:.3f).setDuration(150).setInterpolator(EASE).start();else setAlpha(enabled?1:.3f);}
        @Override protected void onDetachedFromWindow(){if(icon!=null)icon.cancel();animate().cancel();super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas c){float cx=getWidth()/2f,cy=getHeight()/2f,s=Math.min(getWidth(),getHeight());p.setStyle(Paint.Style.FILL);p.setColor(mode==1||mode==2?accent:Color.TRANSPARENT);c.drawCircle(cx,cy,s*.45f,p);p.setColor(mode==1||mode==2?paper:ink);if(mix<1){p.setAlpha(Math.round(255*(1-mix)));glyph(c,oldMode,cx,cy,s);}p.setAlpha(Math.round(255*mix));glyph(c,mode,cx,cy,s);p.setAlpha(255);}
        private void glyph(Canvas c,int value,float cx,float cy,float s){if(value>=4){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(s*.025f);p.setStrokeCap(Paint.Cap.ROUND);if(value==4){c.drawCircle(cx,cy,s*.2f,p);c.drawLine(cx,cy-s*.11f,cx,cy,p);c.drawLine(cx,cy,cx+s*.1f,cy+s*.045f,p);}else{if(value==5)p.setAlpha(Math.round(p.getAlpha()*.45f));c.drawArc(cx-s*.22f,cy-s*.18f,cx+s*.22f,cy+s*.18f,35,285,false,p);c.drawLine(cx+s*.18f,cy-s*.13f,cx+s*.23f,cy-s*.04f,p);c.drawLine(cx+s*.23f,cy-s*.04f,cx+s*.13f,cy-s*.045f,p);if(value>5){p.setStyle(Paint.Style.FILL);p.setTextSize(s*.18f);p.setTextAlign(Paint.Align.CENTER);c.drawText(value==6?"1":"★",cx,cy+s*.055f,p);}}p.setStyle(Paint.Style.FILL);return;}
            if(value==2){c.drawRoundRect(cx-s*.12f,cy-s*.14f,cx-s*.025f,cy+s*.14f,s*.012f,s*.012f,p);c.drawRoundRect(cx+s*.025f,cy-s*.14f,cx+s*.12f,cy+s*.14f,s*.012f,s*.012f,p);}else{float sign=value==0?-1:1;triangle.reset();triangle.moveTo(cx-sign*s*.12f,cy-s*.16f);triangle.lineTo(cx+sign*s*.16f,cy);triangle.lineTo(cx-sign*s*.12f,cy+s*.16f);triangle.close();c.drawPath(triangle,p);if(value==0||value==3)c.drawRoundRect(cx+sign*s*.18f-s*.022f,cy-s*.16f,cx+sign*s*.18f+s*.022f,cy+s*.16f,s*.01f,s*.01f,p);}}
    }
}
