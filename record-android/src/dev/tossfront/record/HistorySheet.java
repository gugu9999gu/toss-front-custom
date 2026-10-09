package dev.tossfront.record;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;

final class HistorySheet {
    private final Context c;private final Appearance a;private final RecordLibrary library;private final SessionRepository repository;private final Dialog dialog;private final boolean overlay;private final Runnable prepare;
    private boolean favorites;
    void dismiss(){dialog.dismiss();}
    HistorySheet(Context c,Appearance a,boolean overlay,Runnable prepare,Runnable dismissed){this.c=c;this.a=a;this.overlay=overlay;this.prepare=prepare;library=RecordLibrary.get(c);repository=SessionRepository.get(c);dialog=new Dialog(c,android.R.style.Theme_Material_Light_NoActionBar);dialog.setOnDismissListener(d->dismissed.run());}
    private int dp(int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size,int color){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private TextView button(String value,Runnable action){TextView b=label(value,14,a.text());b.setGravity(Gravity.CENTER);b.setMinHeight(dp(48));b.setPadding(dp(8),dp(4),dp(8),dp(4));b.setOnClickListener(v->action.run());RecordPanel.press(b,a);return b;}
    void show(){render();Window w=dialog.getWindow();if(overlay)w.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.BOTTOM);dialog.setCanceledOnTouchOutside(true);dialog.show();w.setLayout(-1,-2);w.setDimAmount(.22f);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}
    private void render(){LinearLayout body=new LinearLayout(c);body.setOrientation(1);body.setPadding(dp(20),dp(12),dp(20),dp(16));GradientDrawable bg=new GradientDrawable();bg.setColor(a.surface());bg.setCornerRadius(dp(28));body.setBackground(bg);
        LinearLayout top=new LinearLayout(c);TextView title=label("재생 기록",18,a.text());title.setGravity(Gravity.CENTER_VERTICAL);top.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));top.addView(button("닫기",dialog::dismiss),new LinearLayout.LayoutParams(dp(56),dp(48)));body.addView(top);
        LinearLayout tabs=new LinearLayout(c);TextView recent=button("최근 30일",()->{favorites=false;render();}),pinned=button("★ 즐겨찾기",()->{favorites=true;render();});recent.setTextColor(!favorites?a.accent():a.muted());pinned.setTextColor(favorites?a.accent():a.muted());tabs.addView(recent,new LinearLayout.LayoutParams(0,dp(48),1));tabs.addView(pinned,new LinearLayout.LayoutParams(0,dp(48),1));body.addView(tabs);
        body.addView(label("기록은 30일 후 삭제 · 즐겨찾기는 계속 보관",12,a.muted()));ScrollView scroll=new ScrollView(c);LinearLayout list=new LinearLayout(c);list.setOrientation(1);scroll.addView(list);List<LibraryCore.Entry> entries=library.entries(favorites);
        if(entries.isEmpty()){TextView empty=label(favorites?"★를 눌러 영상을 고정해 주세요.":"재생한 영상이 여기에 표시됩니다.",14,a.muted());empty.setPadding(0,dp(32),0,dp(32));list.addView(empty);}
        for(LibraryCore.Entry e:entries){LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);LinearLayout information=new LinearLayout(c);information.setOrientation(1);information.setPadding(0,dp(12),dp(8),dp(12));TextView name=label(e.title,15,a.text());name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);information.addView(name);String date=new java.text.SimpleDateFormat("M월 d일 HH:mm",Locale.KOREAN).format(new Date(e.played));information.addView(label(date+" · "+RecordPanel.format(e.duration),12,a.muted()));information.setOnClickListener(v->{dialog.dismiss();prepare.run();repository.playVideo(e.id);});row.addView(information,new LinearLayout.LayoutParams(0,-2,1));TextView star=button(library.favorite(e.id)?"★":"☆",()->{library.toggle(e);repository.libraryChanged();render();});star.setContentDescription(library.favorite(e.id)?"즐겨찾기 해제":"즐겨찾기 고정");star.setTextColor(a.accent());star.setTextSize(24);row.addView(star,new LinearLayout.LayoutParams(dp(48),dp(64)));list.addView(row);}
        body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));if(!library.core.favorites.isEmpty())body.addView(button(repository.loopMode()==2?"고정 목록 반복 중 · 루프 버튼으로 해제":"고정 목록만 반복 재생",()->{dialog.dismiss();prepare.run();repository.loopFavorites();}));if(!favorites&&!entries.isEmpty())body.addView(button("최근 기록 지우기",()->{library.clearHistory();repository.libraryChanged();render();}));int height=Math.round(c.getResources().getDisplayMetrics().heightPixels*.8f);dialog.setContentView(body,new ViewGroup.LayoutParams(-1,height));
    }
}
