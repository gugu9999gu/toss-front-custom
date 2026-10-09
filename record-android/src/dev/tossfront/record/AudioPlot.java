package dev.tossfront.record;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Waveform, logarithmic spectrum and a perspective ring, driven exclusively by output samples. */
final class AudioPlot extends View {
    private final AudioSpectrum audio;
    private Appearance appearance;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final float[] wave=new float[128],bands=new float[64],peaks=new float[32];
    private final long[] holds=new long[32];
    private final float[][] history=new float[3][64];
    private static final int DOTS=32*72;
    private final float[] sx=new float[DOTS],sy=new float[DOTS],sz=new float[DOTS],ripple=new float[DOTS];
    private final float[][] buckets=new float[6][DOTS*2];
    private final int[] counts=new int[6],bandOf=new int[DOTS];
    private boolean playing,scheduled,attached;
    private long last,sequence=-1;
    private float phase;
    private final Runnable frame=new Runnable(){public void run(){scheduled=false;if(!active()){audio.stop();last=0;return;}long now=SystemClock.uptimeMillis();float dt=last==0?16:Math.min(64,now-last);last=now;float max=0;
        for(int i=0;i<wave.length;i++){float target=playing?audio.data.waveform[i]*appearance.gain():0;wave[i]+=(target-wave[i])*(1-(float)Math.exp(-dt/60));max=Math.max(max,Math.abs(wave[i]));}
        for(int i=0;i<bands.length;i++){float target=playing?Math.min(1,audio.data.bands[i]*appearance.gain()):0;float tau=target>bands[i]?60:appearance.reduced?400:160;bands[i]+=(target-bands[i])*(1-(float)Math.exp(-dt/tau));max=Math.max(max,bands[i]);}
        if(playing&&audio.connected()&&audio.data.sequence!=sequence){sequence=audio.data.sequence;System.arraycopy(history[1],0,history[2],0,64);System.arraycopy(history[0],0,history[1],0,64);System.arraycopy(bands,0,history[0],0,64);}
        if(playing&&appearance.motion()&&audio.data.peak>.01f)phase=(phase+dt*(float)(Math.PI*2/60000))%(float)(Math.PI*2);
        invalidate();if(playing&&audio.connected()||max>.002f)schedule();
    }};
    AudioPlot(Context c,AudioSpectrum audio){super(c);this.audio=audio;setContentDescription("재생 오디오 시각화");for(int lat=0;lat<32;lat++)for(int lon=0;lon<72;lon++){int i=lat*72+lon;double a=Math.PI*(lat+.5)/32,b=Math.PI*2*lon/72;sx[i]=(float)(Math.sin(a)*Math.cos(b));sy[i]=(float)Math.cos(a);sz[i]=(float)(Math.sin(a)*Math.sin(b));ripple[i]=(float)Math.sin(a*5+b*3);bandOf[i]=(lat*2+lon/9)%64;}}
    void applyAppearance(Appearance a){appearance=a;setContentDescription(Appearance.MODES[a.mode]+" · 재생 오디오 시각화");invalidate();if(active())schedule();}
    void update(boolean value){playing=value;reconcile();}
    private boolean active(){return attached&&isShown()&&getWindowVisibility()==VISIBLE&&appearance!=null&&appearance.mode>0;}
    private void schedule(){if(!scheduled){scheduled=true;postOnAnimationDelayed(frame,appearance!=null&&appearance.mode>=4?24:0);}}
    private void reconcile(){if(active()&&playing){audio.start();schedule();}else{audio.stop();if(active())schedule();else{removeCallbacks(frame);scheduled=false;last=0;}}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;reconcile();}
    @Override protected void onDetachedFromWindow(){attached=false;audio.stop();removeCallbacks(frame);scheduled=false;super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(audio!=null)reconcile();}
    @Override protected void onVisibilityChanged(View view,int visibility){super.onVisibilityChanged(view,visibility);if(audio!=null)reconcile();}
    @Override protected void onDraw(Canvas c){if(appearance==null)return;p.setColor(appearance.accent());p.setAlpha(255);p.setStrokeWidth(dp(2));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
        if(appearance.mode==1)drawWave(c);else if(appearance.mode==2)drawSpectrum(c);else if(appearance.mode==3)drawRing(c);else if(appearance.mode==4)drawSphere(c);else if(appearance.mode==5)drawAurora(c);else drawOrbit(c);
    }
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
    private void drawWave(Canvas c){float left=dp(8),span=getWidth()-left*2,cy=getHeight()*.5f,amplitude=getHeight()*.31f;path.reset();path.moveTo(left,cy-wave[0]*amplitude);
        for(int i=1;i<wave.length;i++){float x=left+span*i/(wave.length-1),prev=left+span*(i-1)/(wave.length-1);path.quadTo(prev,cy-wave[i-1]*amplitude,(prev+x)/2,cy-(wave[i-1]+wave[i])*.5f*amplitude);}path.lineTo(left+span,cy-wave[wave.length-1]*amplitude);c.drawPath(path,p);
    }
    private void drawSpectrum(Canvas c){float width=getWidth(),step=(width-dp(16))/32,bar=Math.max(dp(2),step*.57f),baseline=getHeight()*.74f;long now=SystemClock.uptimeMillis();p.setStyle(Paint.Style.FILL);
        for(int i=0;i<32;i++){float value=Math.max(bands[i*2],bands[i*2+1]),height=Math.max(dp(3),value*getHeight()*.56f),x=dp(8)+step*i;
            c.drawRoundRect(x,baseline-height,x+bar,baseline,bar*.45f,bar*.45f,p);
            if(playing){if(value>=peaks[i]){peaks[i]=value;holds[i]=now+1000;}else if(now>holds[i])peaks[i]=Math.max(value,peaks[i]-.006f);
                p.setAlpha(110);float y=baseline-Math.max(dp(3),peaks[i]*getHeight()*.56f)-dp(5);c.drawRoundRect(x,y,x+bar,y+dp(1.5f),dp(1),dp(1),p);p.setAlpha(255);
            }else{peaks[i]=0;holds[i]=0;}
        }
    }
    private void drawRing(Canvas c){float cx=getWidth()/2f,cy=getHeight()*.53f,radius=Math.min(getWidth(),getHeight())*.37f;
        for(int layer=2;layer>=0;layer--){path.reset();float[] values=layer==0?bands:history[layer];for(int i=0;i<=96;i++){int k=i%96;float a=k*(float)(Math.PI*2/96)+phase;float amount=values[k*64/96];if(!playing)amount=bands[k*64/96];float r=radius*(1+amount*.35f)*(1-layer*.045f);float depth=(float)Math.sin(a);float perspective=1/(1-depth*.13f);float x=cx+(float)Math.cos(a)*r*perspective,y=cy+depth*r*.46f*perspective-layer*dp(6);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();p.setAlpha(layer==0?245:layer==1?85:40);p.setStrokeWidth(dp(layer==0?2:1));c.drawPath(path,p);}p.setAlpha(255);
    }
    private void drawSphere(Canvas c){java.util.Arrays.fill(counts,0);float rotation=appearance.motion()?phase:0,cos=(float)Math.cos(rotation),sin=(float)Math.sin(rotation),radius=Math.min(getWidth(),getHeight())*.32f,cx=getWidth()/2f,cy=getHeight()/2f;
        for(int i=0;i<DOTS;i++){float amount=bands[bandOf[i]],r=1+amount*(.22f+.1f*ripple[i])+wave[(i*13)%128]*.055f;float x=(sx[i]*cos+sz[i]*sin)*r,z=(-sx[i]*sin+sz[i]*cos)*r,y=sy[i]*r;float tiltY=y*.94f-z*.34f,tiltZ=y*.34f+z*.94f,scale=3.4f/(3.4f-tiltZ);int depth=Math.max(0,Math.min(5,(int)((tiltZ+1.45f)*2)));int at=counts[depth];buckets[depth][at]=cx+x*radius*scale;buckets[depth][at+1]=cy+tiltY*radius*scale;counts[depth]+=2;}
        p.setStrokeCap(Paint.Cap.ROUND);for(int depth=0;depth<6;depth++){p.setAlpha(35+depth*42);p.setStrokeWidth(dp(1+depth*.12f));c.drawPoints(buckets[depth],0,counts[depth],p);}p.setAlpha(255);
    }
    private void drawAurora(Canvas c){float left=dp(8),width=getWidth()-left*2,cy=getHeight()/2f;for(int layer=0;layer<5;layer++){path.reset();float offset=(layer-2)*dp(10);for(int i=0;i<128;i++){float x=left+width*i/127f,amplitude=wave[i]*getHeight()*(.2f+layer*.025f)+bands[(i+layer*7)%64]*getHeight()*.035f*(layer-2);float y=cy+offset-amplitude;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}p.setAlpha(layer==2?240:45+layer*15);p.setStrokeWidth(dp(layer==2?2:1));c.drawPath(path,p);}p.setAlpha(255);}
    private void drawOrbit(Canvas c){float cx=getWidth()/2f,cy=getHeight()/2f,radius=Math.min(getWidth(),getHeight())*.34f;for(int layer=0;layer<4;layer++){path.reset();float tilt=.25f+layer*.17f;for(int i=0;i<=96;i++){int k=i%96;float a=k*(float)(Math.PI*2/96),r=radius*(1+bands[(k*64/96+layer*11)%64]*.25f),x=(float)Math.cos(a)*r,y=(float)Math.sin(a)*r*tilt;float rot=layer*.65f+(appearance.motion()?phase:0);float px=cx+x*(float)Math.cos(rot)-y*(float)Math.sin(rot),py=cy+x*(float)Math.sin(rot)+y*(float)Math.cos(rot);if(i==0)path.moveTo(px,py);else path.lineTo(px,py);}path.close();p.setAlpha(210-layer*40);p.setStrokeWidth(dp(1.5f));c.drawPath(path,p);}p.setAlpha(255);}
}
