package dev.tossfront.record;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

/** Draws only the inferred skeleton, never a camera preview. */
final class HandOverlay extends View {
    private static final int[][] EDGES={{0,1},{1,2},{2,3},{3,4},{0,5},{5,6},{6,7},{7,8},{5,9},{9,10},{10,11},{11,12},{9,13},{13,14},{14,15},{15,16},{13,17},{17,18},{18,19},{19,20},{0,17}};
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private List<HandGestureCore.Hand> hands=Collections.emptyList();private int top,height,color;
    HandOverlay(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(false);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    void update(List<HandGestureCore.Hand> value,int color,boolean show){hands=show?value:Collections.emptyList();this.color=color;invalidate();}
    void area(int top,int height){this.top=top;this.height=height;}
    @Override protected void onDraw(Canvas c){float width=getWidth()*.72f,left=getWidth()*.14f,h=height*.8f,y=top+height*.1f;paint.setColor(color);paint.setAlpha(125);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(getResources().getDisplayMetrics().density*1.3f);for(HandGestureCore.Hand hand:hands){for(int[] edge:EDGES)c.drawLine(left+hand.xy[edge[0]*2]*width,y+hand.xy[edge[0]*2+1]*h,left+hand.xy[edge[1]*2]*width,y+hand.xy[edge[1]*2+1]*h,paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(left+hand.xy[16]*width,y+hand.xy[17]*h,getResources().getDisplayMetrics().density*3,paint);paint.setStyle(Paint.Style.STROKE);}}
    @Override public boolean onTouchEvent(android.view.MotionEvent e){return false;}
}
