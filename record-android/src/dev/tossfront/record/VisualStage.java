package dev.tossfront.record;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

final class VisualStage extends FrameLayout {
    private final VinylView vinyl;private final AudioPlot plot;private final FrameLayout external;private SceneView scene;
    final AudioSpectrum audio;private int mode=-1,areaTop,areaHeight=400;private boolean playing;
    VisualStage(Context c,FrameLayout external){super(c);this.external=external;setClipChildren(false);setClipToPadding(false);audio=new AudioSpectrum(c);vinyl=new VinylView(c);plot=new AudioPlot(c,audio);addView(vinyl,new LayoutParams(-1,-1));addView(plot,new LayoutParams(-1,-1));plot.setVisibility(GONE);}
    void setOpenArea(int top,int height){if(areaTop==top&&areaHeight==height)return;areaTop=top;areaHeight=height;requestLayout();}
    @Override protected void onMeasure(int widthSpec,int heightSpec){super.onMeasure(widthSpec,heightSpec);int width=MeasureSpec.makeMeasureSpec(getMeasuredWidth(),MeasureSpec.EXACTLY),height=MeasureSpec.makeMeasureSpec(Math.max(1,areaHeight),MeasureSpec.EXACTLY);vinyl.measure(width,height);plot.measure(width,height);}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){vinyl.layout(0,areaTop,getWidth(),areaTop+areaHeight);plot.layout(0,areaTop,getWidth(),areaTop+areaHeight);if(scene!=null){if(external==null)scene.layout(0,0,getWidth(),getHeight());scene.setOpenArea(areaTop+(external==null?0:getTop()),areaHeight);}}
    void applyAppearance(Appearance a){int next=a.mode;vinyl.applyAppearance(a);plot.applyAppearance(a);if(next>=7&&scene==null){scene=new SceneView(getContext(),audio);scene.setVisibility(GONE);(external==null?this:external).addView(scene,new LayoutParams(-1,-1));}if(scene!=null)scene.applyAppearance(a);
        View shown=next==0?vinyl:next<7?plot:scene;for(View view:new View[]{vinyl,plot,scene})if(view!=null){view.animate().cancel();if(view!=shown){view.setAlpha(0);view.setVisibility(GONE);}}
        shown.setVisibility(VISIBLE);if(mode!=next&&mode>=0&&a.motion()){shown.setAlpha(.5f);shown.animate().alpha(1).setDuration(180).setInterpolator(new PathInterpolator(.22f,1,.36f,1)).start();}else shown.setAlpha(1);mode=next;update(playing,SessionRepository.get(getContext()).artwork());
    }
    void update(boolean value,Bitmap art){playing=value;vinyl.update(playing&&mode==0,art);plot.update(playing&&mode>0&&mode<7);if(scene!=null)scene.update(playing&&mode>=7);}
    void refresh(){vinyl.refreshMotionPolicy();}
    void gestureChanged(){if(mode>0&&mode<7)plot.invalidate();else if(mode>=7&&scene!=null)scene.gestureChanged();}
    String analysisStatus(){if(mode==0||!playing)return "";if(mode>=7&&scene!=null&&!scene.status().isEmpty())return scene.status();return audio.status();}
}
