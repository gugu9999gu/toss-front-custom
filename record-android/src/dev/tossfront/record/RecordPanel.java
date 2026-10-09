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
import android.widget.*;

public final class RecordPanel extends ScrollView implements SessionRepository.Observer {
    public interface Host { void openSource(String packageName); void showRecord(); void connectionSettings(); void close(); }
    static final int PAPER=Color.rgb(241,235,224), INK=Color.rgb(44,48,42), FAINT=Color.rgb(115,117,104), RUST=Color.rgb(172,81,53);
    private final SessionRepository repository;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final VinylView vinyl;
    private final TextView status,title,artist,elapsed,total,hint,connection;
    private final Control previous,toggle,next;
    private final SeekBar seek;
    private boolean attached,dragging;
    private final boolean overlay;
    private int ticks;
    private final Runnable clock=new Runnable(){public void run(){if(!attached)return;updateTime();if(++ticks%4==0)repository.connect();main.postDelayed(this,500);}};

    public RecordPanel(Context context, Host host, boolean overlay) {
        super(context); this.overlay=overlay; repository=SessionRepository.get(context); setFillViewport(true); setBackgroundColor(PAPER);
        if(overlay)setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        setVerticalScrollBarEnabled(false);
        LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(18),dp(24),dp(16));addView(body);
        LinearLayout heading=new LinearLayout(context);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=label("FRONT RECORD",12,INK);brand.setTypeface(Typeface.create("sans-serif-medium",0));brand.setLetterSpacing(.12f);
        heading.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView source=textButton("YouTube",()->host.openSource(repository.packageName()));heading.addView(source,new LinearLayout.LayoutParams(dp(82),dp(44)));
        TextView close=textButton(overlay?"닫기":"홈",host::close);heading.addView(close,new LinearLayout.LayoutParams(dp(52),dp(44)));body.addView(heading);
        status=label("YOUTUBE · WAITING",11,RUST);status.setLetterSpacing(.08f);add(body,status,6);
        if(!overlay){TextView floating=textButton("YouTube 위에 레코드 띄우기",host::showRecord);floating.setBackground(shape(Color.rgb(229,220,203)));add(body,floating,8);}
        vinyl=new VinylView(context);int height=Math.min(295,Math.max(210,(int)(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density*.39f)));
        LinearLayout.LayoutParams recordParams=new LinearLayout.LayoutParams(-1,dp(height));recordParams.topMargin=dp(4);body.addView(vinyl,recordParams);
        title=label("YouTube 재생을 기다리는 중",21,INK);title.setTypeface(Typeface.create("sans-serif-medium",0));title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);add(body,title,8);
        artist=label("YouTube에서 영상을 재생해 주세요.",13,FAINT);artist.setSingleLine(true);artist.setEllipsize(TextUtils.TruncateAt.END);add(body,artist,5);
        seek=new SeekBar(context);seek.setMax(1000);seek.setProgressTintList(ColorStateList.valueOf(RUST));seek.setThumbTintList(ColorStateList.valueOf(RUST));
        seek.setContentDescription("재생 위치");add(body,seek,12);
        LinearLayout time=new LinearLayout(context);elapsed=label("0:00",12,FAINT);total=label("—",12,FAINT);total.setGravity(Gravity.END);
        time.addView(elapsed,new LinearLayout.LayoutParams(0,-2,1));time.addView(total,new LinearLayout.LayoutParams(0,-2,1));body.addView(time);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar bar){dragging=true;}
            public void onProgressChanged(SeekBar bar,int value,boolean user){if(user&&repository.duration()>0)elapsed.setText(format(repository.duration()*value/1000));}
            public void onStopTrackingTouch(SeekBar bar){repository.seek(repository.duration()*bar.getProgress()/1000);dragging=false;}
        });
        LinearLayout controls=new LinearLayout(context);controls.setGravity(Gravity.CENTER);
        previous=new Control(context,0);toggle=new Control(context,1);next=new Control(context,3);
        previous.setOnClickListener(v->repository.previous());toggle.setOnClickListener(v->repository.toggle());next.setOnClickListener(v->repository.next());
        controls.addView(previous,new LinearLayout.LayoutParams(dp(64),dp(64)));
        LinearLayout.LayoutParams center=new LinearLayout.LayoutParams(dp(72),dp(72));center.setMargins(dp(28),0,dp(28),0);controls.addView(toggle,center);
        controls.addView(next,new LinearLayout.LayoutParams(dp(64),dp(64)));add(body,controls,14);
        hint=label("재생 상태와 버튼은 YouTube와 연결됩니다.",12,FAINT);hint.setGravity(Gravity.CENTER);add(body,hint,14);
        LinearLayout sources=new LinearLayout(context);sources.setGravity(Gravity.CENTER);
        TextView app=textButton("YouTube 앱",()->host.openSource(SessionRepository.OFFICIAL));TextView web=textButton("YouTube 웹",()->host.openSource(SessionRepository.WEB));
        sources.addView(app,new LinearLayout.LayoutParams(0,dp(48),1));sources.addView(web,new LinearLayout.LayoutParams(0,dp(48),1));add(body,sources,14);
        connection=textButton("재생 정보 연결 설정",host::connectionSettings);add(body,connection,8);
        TextView output=textButton("오디오 출력 변경",()->{
            Intent intent=context.getPackageManager().getLaunchIntentForPackage("dev.tossfront.audio");
            if(intent!=null){intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(intent);}
        });add(body,output,4);
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView label(String s,int size,int color){TextView v=new TextView(getContext());v.setText(s);v.setTextSize(size);v.setTextColor(color);return v;}
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
    private void add(LinearLayout parent,View view,int margin){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(margin);parent.addView(view,p);}
    private TextView textButton(String text,Runnable action){TextView v=label(text,13,INK);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(48));v.setPadding(dp(8),dp(10),dp(8),dp(10));v.setClickable(true);v.setFocusable(true);v.setOnClickListener(x->action.run());press(v);return v;}
    static void press(View view){view.setOnTouchListener((v,event)->{int action=event.getAction();if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){float scale=action==MotionEvent.ACTION_DOWN?.97f:1;v.animate().scaleX(scale).scaleY(scale).setDuration(ValueAnimator.areAnimatorsEnabled()?100:0).start();}return false;});}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;repository.observe(this);main.post(clock);}
    @Override protected void onDetachedFromWindow(){attached=false;main.removeCallbacks(clock);repository.remove(this);super.onDetachedFromWindow();}
    @Override public void changed(){
        title.setText(repository.title());artist.setText(repository.artist());vinyl.update(repository.playing(),repository.artwork());
        String state=repository.playing()?"PLAYING":repository.buffering()?"BUFFERING":repository.error()?"ERROR":repository.paused()?"PAUSED":repository.connected()?"STOPPED":"WAITING";
        status.setText((repository.connected()?repository.source().toUpperCase():"YOUTUBE")+"  ·  "+state);
        toggle.mode=repository.playing()?2:1;toggle.setContentDescription(repository.playing()?"일시정지":"재생");
        toggle.setEnabled(repository.supports(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE));toggle.invalidate();
        previous.setEnabled(repository.supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS));next.setEnabled(repository.supports(PlaybackState.ACTION_SKIP_TO_NEXT));
        seek.setEnabled(repository.supports(PlaybackState.ACTION_SEEK_TO)&&repository.duration()>0);
        connection.setVisibility(repository.hasAccess()?GONE:VISIBLE);
        hint.setText(!repository.hasAccess()?"재생 정보 연결을 허용해 주세요.":repository.error()?"YouTube에서 재생 오류를 확인해 주세요.":!repository.connected()?"YouTube 앱 또는 웹에서 영상을 먼저 재생하세요.":repository.buffering()?"YouTube에서 영상을 불러오는 중입니다.":!overlay&&SessionRepository.WEB.equals(repository.packageName())?"웹 재생에는 위의 레코드 띄우기 버튼을 사용하세요.":"재생 상태와 버튼은 YouTube와 연결됩니다.");
        setKeepScreenOn(repository.playing());updateTime();
    }
    private void updateTime(){if(!dragging){elapsed.setText(format(repository.position()));total.setText(repository.duration()>0?format(repository.duration()):"—");seek.setProgress(repository.duration()>0?(int)(repository.position()*1000/repository.duration()):0);}}
    static String format(long ms){long seconds=Math.max(0,ms/1000);return seconds>=3600?String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60):String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}

    static final class Control extends View {
        int mode;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Control(Context context,int mode){super(context);this.mode=mode;setClickable(true);setFocusable(true);setContentDescription(mode==0?"이전 영상":mode==3?"다음 영상":"재생");press(this);}
        @Override public void setEnabled(boolean enabled){super.setEnabled(enabled);setAlpha(enabled?1:.28f);}
        @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight(),cx=w/2,cy=h/2,s=Math.min(w,h);p.setStyle(Paint.Style.FILL);p.setColor(mode==1||mode==2?RUST:Color.TRANSPARENT);c.drawCircle(cx,cy,s*.47f,p);p.setColor(mode==1||mode==2?PAPER:INK);
            if(mode==2){c.drawRoundRect(cx-s*.12f,cy-s*.14f,cx-s*.025f,cy+s*.14f,s*.012f,s*.012f,p);c.drawRoundRect(cx+s*.025f,cy-s*.14f,cx+s*.12f,cy+s*.14f,s*.012f,s*.012f,p);}
            else{Path path=new Path();float sign=mode==0?-1:1;path.moveTo(cx-sign*s*.12f,cy-s*.16f);path.lineTo(cx+sign*s*.16f,cy);path.lineTo(cx-sign*s*.12f,cy+s*.16f);path.close();c.drawPath(path,p);if(mode==0||mode==3)c.drawRoundRect(cx+sign*s*.18f-s*.022f,cy-s*.16f,cx+sign*s*.18f+s*.022f,cy+s*.16f,s*.01f,s*.01f,p);}
        }
    }
}
