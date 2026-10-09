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
    private boolean playing,scheduled,attached,ownsAudio;
    private Shader tint;private int tintColor;
    private long last,sequence=-1;
    private float phase;
    private final Runnable frame=new Runnable(){public void run(){scheduled=false;if(!active()){releaseAudio();last=0;return;}long now=SystemClock.uptimeMillis();float dt=last==0?16:Math.min(64,now-last);last=now;float max=0;

        for(int i=0;i<wave.length;i++){float target=playing?ResponseMath.signed(audio.data.waveform[i],appearance.gain()):0;wave[i]+=(target-wave[i])*(1-(float)Math.exp(-dt/60));max=Math.max(max,Math.abs(wave[i]));}
        for(int i=0;i<bands.length;i++){float target=playing?ResponseMath.band(audio.data.bands[i],appearance.gain()):0;float tau=target>bands[i]?60:appearance.reduced?400:160;bands[i]+=(target-bands[i])*(1-(float)Math.exp(-dt/tau));max=Math.max(max,bands[i]);}
        if(playing&&audio.connected()&&audio.data.sequence!=sequence){sequence=audio.data.sequence;System.arraycopy(history[1],0,history[2],0,64);System.arraycopy(history[0],0,history[1],0,64);System.arraycopy(bands,0,history[0],0,64);}
        if(playing&&appearance.motion()&&audio.data.peak>.01f)phase=(phase+dt*(float)(Math.PI*2/60000))%(float)(Math.PI*2);
        invalidate();if(playing&&audio.connected()||max>.002f)schedule();
    }};
    AudioPlot(Context c,AudioSpectrum audio){super(c);this.audio=audio;setContentDescription("재생 오디오 시각화");for(int lat=0;lat<32;lat++)for(int lon=0;lon<72;lon++){int i=lat*72+lon;double a=Math.PI*(lat+.5)/32,b=Math.PI*2*lon/72;sx[i]=(float)(Math.sin(a)*Math.cos(b));sy[i]=(float)Math.cos(a);sz[i]=(float)(Math.sin(a)*Math.sin(b));ripple[i]=(float)Math.sin(a*5+b*3);bandOf[i]=(lat*2+lon/9)%64;}}
    void applyAppearance(Appearance a){appearance=a;updateTint();setContentDescription(Appearance.MODES[a.mode]+" · 재생 오디오 시각화");invalidate();if(active())schedule();}
    void update(boolean value){playing=value;reconcile();}
    private boolean active(){return attached&&isShown()&&getWindowVisibility()==VISIBLE&&appearance!=null&&appearance.mode>0&&appearance.mode<7;}
    private void schedule(){if(!scheduled){scheduled=true;postOnAnimationDelayed(frame,appearance!=null&&appearance.mode>=4?24:0);}}
    private void releaseAudio(){if(ownsAudio){audio.stop();ownsAudio=false;}}
    private void reconcile(){if(active()&&playing){ownsAudio=true;audio.start();schedule();}else{releaseAudio();if(active())schedule();else{removeCallbacks(frame);scheduled=false;last=0;}}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;reconcile();}
    @Override protected void onDetachedFromWindow(){attached=false;releaseAudio();removeCallbacks(frame);scheduled=false;super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(audio!=null)reconcile();}
    @Override protected void onVisibilityChanged(View view,int visibility){super.onVisibilityChanged(view,visibility);if(audio!=null)reconcile();}
    private void updateTint(){if(appearance!=null){int[] colors=appearance.visualColors();tintColor=colors[0];tint=new LinearGradient(0,0,Math.max(1,getWidth()),Math.max(1,getHeight()),colors,null,Shader.TileMode.CLAMP);}}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);updateTint();}
    @Override protected void onDraw(Canvas c){if(appearance==null)return;p.setColor(tintColor);p.setShader(tint);p.setAlpha(255);p.setStrokeWidth(dp(2));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
        c.save();
        if(appearance.mode==1)drawWave(c);else if(appearance.mode==2)drawSpectrum(c);else if(appearance.mode==3)drawRing(c);else if(appearance.mode==4)drawSphere(c);else if(appearance.mode==5)drawAurora(c);else drawOrbit(c);
        c.restore();if(appearance.theme==4){p.setShader(null);p.setColor(0xff0f380f);p.setAlpha(16);p.setStrokeWidth(1);for(int y=0;y<getHeight();y+=6)c.drawLine(0,y,getWidth(),y,p);p.setAlpha(255);}
    }
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
    private float w(int i){return Math.signum(wave[i])*ResponseMath.display(Math.abs(wave[i]));}
    private void field(float x,float y,float energy){audio.gesture.displace(x/Math.max(1,getWidth()),y/Math.max(1,getHeight()),energy,getWidth()/(float)Math.max(1,getHeight()));}
    private float waveY(int i,float cy,float amplitude){float x=dp(8)+(getWidth()-dp(16))*i/(wave.length-1),y=cy-w(i)*amplitude;field(x,y,Math.abs(w(i)));return y+audio.gesture.dy*getHeight();}
    private void drawWave(Canvas c){float left=dp(8),span=getWidth()-left*2,cy=getHeight()*.5f,amplitude=getHeight()*.29f;path.reset();path.moveTo(left,waveY(0,cy,amplitude));
        for(int i=1;i<wave.length;i++){float x=left+span*i/(wave.length-1),prev=left+span*(i-1)/(wave.length-1);path.quadTo(prev,waveY(i-1,cy,amplitude),(prev+x)/2,(waveY(i-1,cy,amplitude)+waveY(i,cy,amplitude))*.5f);}path.lineTo(left+span,waveY(wave.length-1,cy,amplitude));c.drawPath(path,p);
    }
    private void drawSpectrum(Canvas c){float width=getWidth(),step=(width-dp(16))/32,bar=Math.max(dp(2),step*.57f),baseline=getHeight()*.87f;long now=SystemClock.uptimeMillis();p.setStyle(Paint.Style.FILL);
        for(int i=0;i<32;i++){float value=Math.max(bands[i*2],bands[i*2+1]),height=Math.max(dp(3),ResponseMath.display(value)*getHeight()*.48f),x=dp(8)+step*i;
            field(x+bar*.5f,baseline-height,ResponseMath.display(value));height=Math.max(dp(3),height-audio.gesture.dy*getHeight());
            c.drawRoundRect(x,baseline-height,x+bar,baseline,bar*.45f,bar*.45f,p);
            if(playing){if(value>=peaks[i]){peaks[i]=value;holds[i]=now+1000;}else if(now>holds[i])peaks[i]=Math.max(value,peaks[i]-.006f);
                p.setAlpha(110);float y=baseline-Math.max(dp(3),ResponseMath.display(peaks[i])*getHeight()*.48f)-dp(5);c.drawRoundRect(x,y,x+bar,y+dp(1.5f),dp(1),dp(1),p);p.setAlpha(255);
            }else{peaks[i]=0;holds[i]=0;}
        }
    }
    private void drawRing(Canvas c){float cx=getWidth()/2f,cy=getHeight()*.53f,radius=Math.min(getWidth(),getHeight())*.27f;
        for(int layer=2;layer>=0;layer--){path.reset();float[] values=layer==0?bands:history[layer];for(int i=0;i<=96;i++){int k=i%96;float a=k*(float)(Math.PI*2/96)+phase;float amount=values[k*64/96];if(!playing)amount=bands[k*64/96];float r=radius*(1+ResponseMath.display(amount)*.35f)*(1-layer*.045f);float depth=(float)Math.sin(a);float perspective=1/(1-depth*.13f);float x=cx+(float)Math.cos(a)*r*perspective,y=cy+depth*r*.46f*perspective-layer*dp(6);field(x,y,ResponseMath.display(amount));x+=audio.gesture.dx*getWidth();y+=audio.gesture.dy*getHeight();if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();p.setAlpha(layer==0?245:layer==1?85:40);p.setStrokeWidth(dp(layer==0?2:1));c.drawPath(path,p);}p.setAlpha(255);
    }
    private void drawSphere(Canvas c){java.util.Arrays.fill(counts,0);float rotation=appearance.motion()?phase:0,cos=(float)Math.cos(rotation),sin=(float)Math.sin(rotation),radius=Math.min(getWidth(),getHeight())*.26f,cx=getWidth()/2f,cy=getHeight()/2f;
        for(int i=0;i<DOTS;i++){float amount=bands[bandOf[i]],r=Math.max(.55f,Math.min(1.78f,1+amount*(.18f+.065f*ripple[i])+wave[(i*13)%128]*.035f));float x=(sx[i]*cos+sz[i]*sin)*r,z=(-sx[i]*sin+sz[i]*cos)*r,y=sy[i]*r;float tiltY=y*.94f-z*.34f,tiltZ=y*.34f+z*.94f,scale=6f/(6f-tiltZ);int depth=Math.max(0,Math.min(5,(int)((tiltZ+1.45f)*2)));int at=counts[depth];float px=cx+x*radius*scale,py=cy+tiltY*radius*scale;field(px,py,ResponseMath.display(amount));buckets[depth][at]=px+audio.gesture.dx*getWidth();buckets[depth][at+1]=py+audio.gesture.dy*getHeight();counts[depth]+=2;}
        p.setStrokeCap(Paint.Cap.ROUND);for(int depth=0;depth<6;depth++){p.setAlpha(35+depth*42);p.setStrokeWidth(dp(1+depth*.12f));c.drawPoints(buckets[depth],0,counts[depth],p);}p.setAlpha(255);
    }
    private void drawAurora(Canvas c){float left=dp(8),width=getWidth()-left*2,cy=getHeight()/2f;for(int layer=0;layer<5;layer++){path.reset();float offset=(layer-2)*dp(10);for(int i=0;i<128;i++){float x=left+width*i/127f,amplitude=w(i)*getHeight()*(.15f+layer*.025f)+ResponseMath.display(bands[(i+layer*7)%64])*getHeight()*.024f*(layer-2);float y=cy+offset-Math.max(-getHeight()*.42f,Math.min(getHeight()*.42f,amplitude));field(x,y,Math.abs(w(i)));x+=audio.gesture.dx*getWidth();y+=audio.gesture.dy*getHeight();if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}p.setAlpha(layer==2?240:45+layer*15);p.setStrokeWidth(dp(layer==2?2:1));c.drawPath(path,p);}p.setAlpha(255);}
    private void drawOrbit(Canvas c){float cx=getWidth()/2f,cy=getHeight()/2f,radius=Math.min(getWidth(),getHeight())*.31f;for(int layer=0;layer<4;layer++){path.reset();float tilt=.25f+layer*.17f;for(int i=0;i<=96;i++){int k=i%96;float a=k*(float)(Math.PI*2/96),r=radius*(1+ResponseMath.display(bands[(k*64/96+layer*11)%64])*.25f),x=(float)Math.cos(a)*r,y=(float)Math.sin(a)*r*tilt;float rot=layer*.65f+(appearance.motion()?phase:0);float px=cx+x*(float)Math.cos(rot)-y*(float)Math.sin(rot),py=cy+x*(float)Math.sin(rot)+y*(float)Math.cos(rot);field(px,py,ResponseMath.display(bands[k*64/96]));px+=audio.gesture.dx*getWidth();py+=audio.gesture.dy*getHeight();if(i==0)path.moveTo(px,py);else path.lineTo(px,py);}path.close();p.setAlpha(210-layer*40);p.setStrokeWidth(dp(1.5f));c.drawPath(path,p);}p.setAlpha(255);}
}
