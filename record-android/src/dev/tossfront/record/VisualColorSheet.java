package dev.tossfront.record;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Touch-only HSV controls work without opening the IME over the playing web view. */
final class VisualColorSheet {
    private final Context context;private final Appearance a;private final Runnable changed,closed;
    private final Dialog dialog;private int slot;private boolean binding;
    private final float[] hsv=new float[3];private TextView code;private View preview;
    private final SeekBar[] bars=new SeekBar[3];private final TextView[] slots=new TextView[3];
    VisualColorSheet(Context c,Appearance a,boolean overlay,Runnable changed,Runnable closed){context=c;this.a=a;this.changed=changed;this.closed=closed;dialog=new Dialog(c,android.R.style.Theme_Material_Light_NoActionBar);if(overlay)dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);dialog.setOnDismissListener(d->closed.run());}
    void dismiss(){dialog.dismiss();}
    private int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    private TextView text(String label,int size){TextView v=new TextView(context);v.setText(label);v.setTextSize(size);v.setTextColor(a.text());v.setMinHeight(dp(40));v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
    private void choose(int next){slot=next;Color.colorToHSV(a.customColors[slot],hsv);binding=true;bars[0].setProgress(Math.round(hsv[0]));bars[1].setProgress(Math.round(hsv[1]*100));bars[2].setProgress(Math.round(hsv[2]*100));binding=false;refresh();}
    private void refresh(){code.setText(String.format(java.util.Locale.ROOT,"색 %d · #%06X",slot+1,a.customColors[slot]&0xffffff));preview.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,a.visualColors()));for(int i=0;i<3;i++){GradientDrawable d=shape(a.customColors[i]);if(i==slot)d.setStroke(dp(3),a.text());slots[i].setBackground(d);slots[i].setTextColor(Color.luminance(a.customColors[i])>.45f?Color.rgb(24,26,30):Color.WHITE);}}
    void show(){LinearLayout body=new LinearLayout(context);body.setOrientation(1);body.setPadding(dp(24),dp(16),dp(24),dp(24));body.setBackground(shape(a.surface()));FrameLayout header=new FrameLayout(context);FrameLayout.LayoutParams titleSize=new FrameLayout.LayoutParams(-1,dp(48));titleSize.rightMargin=dp(64);header.addView(text("시각화 색상",18),titleSize);TextView close=text("닫기",14);close.setGravity(Gravity.CENTER);close.setContentDescription("시각화 색상 닫기");close.setOnClickListener(v->dismiss());header.addView(close,new FrameLayout.LayoutParams(dp(56),dp(48),Gravity.END));body.addView(header,new LinearLayout.LayoutParams(-1,dp(48)));preview=new View(context);body.addView(preview,new LinearLayout.LayoutParams(-1,dp(36)));LinearLayout row=new LinearLayout(context);for(int i=0;i<3;i++){final int index=i;slots[i]=text("색 "+(i+1),14);slots[i].setGravity(Gravity.CENTER);slots[i].setOnClickListener(v->choose(index));slots[i].setContentDescription("그라데이션 색 "+(i+1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(4),dp(8),dp(4),dp(8));row.addView(slots[i],p);}body.addView(row);code=text("",14);body.addView(code);
        String[] labels={"색조","채도","밝기"};for(int i=0;i<3;i++){final int index=i;body.addView(text(labels[i],13));SeekBar bar=new SeekBar(context);bars[i]=bar;bar.setMax(i==0?360:100);bar.setContentDescription(labels[i]);bar.setProgressTintList(ColorStateList.valueOf(a.accent()));bar.setThumbTintList(ColorStateList.valueOf(a.accent()));bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int value,boolean user){if(user&&!binding){hsv[index]=index==0?value:value/100f;a.customColors[slot]=Color.HSVToColor(hsv);if(a.visualPalette!=0)a.visualPalette=5;a.save();refresh();changed.run();}}public void onStopTrackingTouch(SeekBar b){}});body.addView(bar,new LinearLayout.LayoutParams(-1,dp(48)));}
        body.addView(text("단색은 색 1, 직접 설정은 세 가지 색을 사용합니다.",12));dialog.setContentView(body,new ViewGroup.LayoutParams(-1,-2));Window w=dialog.getWindow();w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.BOTTOM);dialog.show();w.setLayout(-1,-2);choose(0);
    }
}
