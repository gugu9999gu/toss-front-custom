package dev.tossfront.record;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Native vector turntable. Only actual PLAYING state animates; paused views use no frame loop. */
public final class VinylView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap artwork;
    private boolean playing;
    private float angle;
    private long lastFrame;

    public VinylView(Context context) { super(context); setContentDescription("재생을 기다리는 레코드"); }
    public void update(boolean playing, Bitmap artwork) {
        if (this.playing != playing) lastFrame = 0;
        this.playing = playing; this.artwork = artwork;
        setContentDescription(playing ? "재생 중 · 회전하는 레코드" : "정지한 레코드"); invalidate();
    }
    private void fill(int color) { paint.setShader(null); paint.setColor(color); paint.setStyle(Paint.Style.FILL); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight(), size = Math.min(w, h * 1.12f);
        float cx = w * .47f, cy = h * .51f, radius = size * .425f;
        if (playing && ValueAnimator.areAnimatorsEnabled()) {
            long now = SystemClock.uptimeMillis();
            if (lastFrame > 0) angle = (angle + Math.min(now - lastFrame, 100) * .0225f) % 360;
            lastFrame = now;
        } else lastFrame = 0;
        fill(Color.argb(22, 53, 41, 28)); canvas.drawOval(cx - radius, cy - radius + 12, cx + radius, cy + radius + 12, paint);
        fill(Color.rgb(185, 177, 158)); canvas.drawCircle(cx, cy, radius + 5, paint);
        paint.setShader(new RadialGradient(cx - radius*.2f, cy - radius*.3f, radius*1.4f,
                new int[]{Color.rgb(55,55,52), Color.rgb(22,23,23), Color.rgb(9,10,10)}, new float[]{0,.55f,1}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, paint); paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(.7f, radius*.003f));
        for (int i = 0; i < 43; i++) {
            float groove = radius * (.36f + i * .014f); paint.setColor(i % 4 == 0 ? Color.argb(67,139,139,128) : Color.argb(38,144,144,130));
            canvas.drawCircle(cx, cy, groove, paint);
        }
        RectF disc = new RectF(cx-radius*.97f, cy-radius*.97f, cx+radius*.97f, cy+radius*.97f);
        paint.setStrokeWidth(radius*.55f); paint.setColor(Color.argb(14,255,255,240));
        canvas.drawArc(disc, 218, 35, false, paint); canvas.drawArc(disc, 40, 24, false, paint);
        canvas.save(); canvas.rotate(angle, cx, cy);
        float label = radius * .33f;
        fill(Color.rgb(173,83,56)); canvas.drawCircle(cx, cy, label, paint);
        if (artwork != null && !artwork.isRecycled()) {
            Path clip = new Path(); clip.addCircle(cx,cy,label*.83f,Path.Direction.CW); canvas.save(); canvas.clipPath(clip);
            int shortest = Math.min(artwork.getWidth(), artwork.getHeight());
            Rect source = new Rect((artwork.getWidth()-shortest)/2,(artwork.getHeight()-shortest)/2,(artwork.getWidth()+shortest)/2,(artwork.getHeight()+shortest)/2);
            canvas.drawBitmap(artwork,source,new RectF(cx-label*.83f,cy-label*.83f,cx+label*.83f,cy+label*.83f),paint); canvas.restore();
        } else {
            fill(Color.rgb(247,231,207)); paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            paint.setTextSize(label*.22f); canvas.drawText("FRONT",cx,cy-label*.25f,paint); canvas.drawText("RECORD",cx,cy+label*.35f,paint);
        }
        canvas.restore();
        fill(Color.rgb(221,211,190)); canvas.drawCircle(cx,cy,radius*.035f,paint);
        fill(Color.rgb(18,22,22)); canvas.drawCircle(cx,cy,radius*.017f,paint);
        float px=w*.87f, py=h*.16f;
        float tx=playing ? cx+radius*.64f : w*.92f, ty=playing ? cy+radius*.40f : h*.80f;
        fill(Color.argb(28,39,33,22)); canvas.drawCircle(px+3,py+5,radius*.095f,paint);
        fill(Color.rgb(100,105,100)); canvas.drawCircle(px,py,radius*.092f,paint);
        fill(Color.rgb(208,205,192)); canvas.drawCircle(px,py,radius*.061f,paint);
        Path arm=new Path(); arm.moveTo(px,py); arm.lineTo(px-(px-tx)*.32f, py+(ty-py)*.55f); arm.lineTo(tx,ty);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(Color.argb(45,19,22,19)); paint.setStrokeWidth(radius*.05f); canvas.save(); canvas.translate(3,5); canvas.drawPath(arm,paint); canvas.restore();
        paint.setColor(Color.rgb(121,126,120)); paint.setStrokeWidth(radius*.043f); canvas.drawPath(arm,paint);
        paint.setColor(Color.rgb(236,232,213)); paint.setStrokeWidth(radius*.018f); canvas.drawPath(arm,paint);
        fill(Color.rgb(49,57,51)); canvas.save(); canvas.rotate(playing ? -34 : -10,tx,ty);
        canvas.drawRoundRect(tx-radius*.045f,ty-radius*.01f,tx+radius*.045f,ty+radius*.11f,radius*.012f,radius*.012f,paint);
        fill(Color.rgb(178,91,61)); canvas.drawRect(tx-radius*.038f,ty+radius*.085f,tx+radius*.038f,ty+radius*.105f,paint); canvas.restore();
        if (playing && ValueAnimator.areAnimatorsEnabled() && isShown() && getWindowVisibility()==VISIBLE) postInvalidateDelayed(33);
    }
}
