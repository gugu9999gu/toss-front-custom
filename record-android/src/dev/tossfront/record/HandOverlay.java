package dev.tossfront.record;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.util.*;

/** Inferred skeleton and memory-only pinch trails; never a camera preview. */
final class HandOverlay extends View {
    private static final int[][] EDGES={{0,1},{1,2},{2,3},{3,4},{0,5},{5,6},{6,7},{7,8},{5,9},{9,10},{10,11},{11,12},{9,13},{13,14},{14,15},{15,16},{13,17},{17,18},{18,19},{19,20},{0,17}};
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private List<HandGestureCore.Hand> hands=Collections.emptyList();private int color;
    private final HandDisplay display=new HandDisplay();
    private final PinchTrail trail=new PinchTrail();private final Path path=new Path();private boolean scheduled;
    private final Runnable fade=new Runnable(){public void run(){scheduled=false;long now=SystemClock.elapsedRealtime();trail.advance(now);display.advance(now);hands=display.hands;invalidate();schedule();}};
    HandOverlay(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(false);setLayerType(View.LAYER_TYPE_NONE,null);}
    void update(List<HandGestureCore.Hand> value,int color,boolean show,boolean showTrail){long now=SystemClock.elapsedRealtime();if(show)display.update(value,now);else display.clear();hands=display.hands;this.color=color;trail.update(value,now,showTrail);invalidate();schedule();}
    void clear(){display.clear();hands=Collections.emptyList();trail.clear();removeCallbacks(fade);scheduled=false;invalidate();}
    private void schedule(){if(!scheduled&&(trail.fading()||display.fading()||display.moving())&&isShown()){scheduled=true;if(display.moving())postOnAnimation(fade);else postOnAnimationDelayed(fade,33);}}
    @Override protected void onDetachedFromWindow(){clear();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas c){float width=getWidth(),left=0,h=getHeight(),y=0,density=getResources().getDisplayMetrics().density;long now=SystemClock.elapsedRealtime();display.advance(now);hands=display.hands;paint.setColor(color);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStyle(Paint.Style.STROKE);
        for(PinchTrail.Stroke s:trail.strokes){if(s.count==0)continue;path.reset();path.moveTo(left+s.xs[0]*width,y+s.ys[0]*h);for(int i=1;i<s.count;i++){float px=left+s.xs[i-1]*width,py=y+s.ys[i-1]*h,nx=left+s.xs[i]*width,ny=y+s.ys[i]*h;path.quadTo(px,py,(px+nx)*.5f,(py+ny)*.5f);}path.lineTo(left+s.xs[s.count-1]*width,y+s.ys[s.count-1]*h);float alpha=s.alpha(now);paint.setAlpha(Math.round(40*alpha));paint.setStrokeWidth(density*7);c.drawPath(path,paint);paint.setAlpha(Math.round(215*alpha));paint.setStrokeWidth(density*2.2f);c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(left+s.xs[s.count-1]*width,y+s.ys[s.count-1]*h,density*3,paint);paint.setStyle(Paint.Style.STROKE);}
        paint.setAlpha(125);paint.setStrokeWidth(density*1.3f);for(int i=0;i<hands.size();i++){HandGestureCore.Hand hand=hands.get(i);paint.setAlpha(Math.round(125*display.alphas[i]));for(int[] edge:EDGES)c.drawLine(left+hand.xy[edge[0]*2]*width,y+hand.xy[edge[0]*2+1]*h,left+hand.xy[edge[1]*2]*width,y+hand.xy[edge[1]*2+1]*h,paint);paint.setStyle(Paint.Style.FILL);boolean pinch=hand.pinchRatio<=.78f;c.drawCircle(left+(pinch?hand.pinchX:hand.x)*width,y+(pinch?hand.pinchY:hand.y)*h,density*(pinch?5:3),paint);paint.setStyle(Paint.Style.STROKE);}schedule();}
    @Override public boolean onTouchEvent(android.view.MotionEvent e){return false;}
}
