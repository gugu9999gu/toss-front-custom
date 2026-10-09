package dev.tossfront.record;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

final class VisualStage extends FrameLayout {
    private final VinylView vinyl;
    private final AudioPlot plot;
    final AudioSpectrum audio;
    private int mode=-1;
    private Appearance appearance;
    private boolean playing;
    VisualStage(Context c){super(c);audio=new AudioSpectrum(c);vinyl=new VinylView(c);plot=new AudioPlot(c,audio);addView(vinyl,new LayoutParams(-1,-1));addView(plot,new LayoutParams(-1,-1));plot.setVisibility(GONE);}
    void applyAppearance(Appearance a){appearance=a;vinyl.applyAppearance(a);plot.applyAppearance(a);int next=a.mode;View shown=next==0?vinyl:plot,hidden=next==0?plot:vinyl;
        shown.animate().cancel();hidden.animate().cancel();shown.setVisibility(VISIBLE);
        if(mode!=next&&mode>=0&&a.motion()){shown.setAlpha(.25f);shown.animate().alpha(1).setDuration(180).setInterpolator(new PathInterpolator(.22f,1,.36f,1)).start();hidden.animate().alpha(0).setDuration(180).withEndAction(()->hidden.setVisibility(GONE)).start();}
        else{shown.setAlpha(1);hidden.setAlpha(0);hidden.setVisibility(GONE);}
        mode=next;plot.update(playing&&mode>0);vinyl.update(playing&&mode==0,SessionRepository.get(getContext()).artwork());
    }
    void update(boolean playing,Bitmap art){this.playing=playing;vinyl.update(playing&&mode==0,art);plot.update(playing&&mode>0);}
    void refresh(){vinyl.refreshMotionPolicy();}
    String analysisStatus(){return mode==0||!playing?"":audio.status();}
}
