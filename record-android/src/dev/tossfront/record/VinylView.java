package dev.tossfront.record;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

/** Cached vector layers rotate with view transforms; grooves are not repainted every frame. */
public final class VinylView extends FrameLayout {
    private static final PathInterpolator EASE = new PathInterpolator(.22f, 1, .36f, 1);
    private final FrameLayout disc;
    private final ArtworkLayer[] covers;
    private final ArmLayer arm;
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RecordMotion motion = new RecordMotion();
    private Bitmap artwork;
    private Appearance appearance;
    private boolean playing, attached, scheduled, policy;
    private int front;
    private float cx, cy, radius, engaged;
    private final Runnable frame = new Runnable() { public void run() {
        scheduled = false;
        if (!motionAllowed()) { refreshMotionPolicy(); return; }
        disc.setRotation(motion.tick(SystemClock.uptimeMillis()));
        if (motion.moving()) schedule();
    }};

    public VinylView(Context context) {
        super(context); setWillNotDraw(false); setClipChildren(false);
        disc = new FrameLayout(context); disc.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        disc.addView(new PlatterLayer(context), new LayoutParams(-1, -1));
        covers = new ArtworkLayer[]{new ArtworkLayer(context), new ArtworkLayer(context)};
        for (ArtworkLayer cover : covers) disc.addView(cover, new LayoutParams(-1, -1));
        covers[1].setAlpha(0); addView(disc);
        arm = new ArmLayer(context); arm.setLayerType(View.LAYER_TYPE_HARDWARE, null); addView(arm);
        setContentDescription("재생을 기다리는 레코드");
    }

    public void update(boolean playing, Bitmap image) {
        boolean stateChanged = this.playing != playing;
        this.playing = playing;
        setContentDescription(playing ? "재생 중 · 회전하는 레코드" : "정지한 레코드");
        if (artwork != image) {
            artwork = image;
            int next = 1 - front;
            covers[next].animate().cancel(); covers[front].animate().cancel();
            covers[next].setArtwork(image); covers[next].setAlpha(0);
            if (motionAllowed()) {
                covers[next].animate().alpha(1).setDuration(180).setInterpolator(EASE).start();
                covers[front].animate().alpha(0).setDuration(180).setInterpolator(EASE).start();
            } else { covers[next].setAlpha(1); covers[front].setAlpha(0); }
            front = next;
        }
        refreshMotionPolicy();
        if (stateChanged) moveArm();
        if (motionAllowed()) { motion.setPlaying(playing, SystemClock.uptimeMillis()); if (motion.moving()) schedule(); }
    }

    public void applyAppearance(Appearance value) { appearance=value; ((PlatterLayer)disc.getChildAt(0)).setColors(value.visualColors()); disc.getChildAt(0).invalidate(); invalidate(); refreshMotionPolicy(); }
    private boolean motionAllowed() { return attached && isShown() && getWindowVisibility() == VISIBLE && (appearance==null?ValueAnimator.areAnimatorsEnabled():appearance.motion()); }
    private void schedule() { if (!scheduled) { scheduled = true; postOnAnimation(frame); } }
    private void suspend() { removeCallbacks(frame); scheduled = false; motion.suspend(); }
    public void refreshMotionPolicy() {
        boolean enabled = motionAllowed();
        if (enabled == policy) return;
        policy = enabled;
        if (enabled) { motion.setPlaying(playing, SystemClock.uptimeMillis()); if (motion.moving()) schedule(); }
        else { suspend(); for (int i = 0; i < covers.length; i++) { covers[i].animate().cancel(); covers[i].setAlpha(i == front ? 1 : 0); } }
        moveArm();
    }
    private void moveArm() {
        arm.animate().cancel();
        float target = playing ? engaged : 0;
        if (motionAllowed()) arm.animate().rotation(target).setDuration(240).setInterpolator(EASE).start();
        else arm.setRotation(target);
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); attached = true; refreshMotionPolicy(); }
    @Override protected void onDetachedFromWindow() { attached = false; suspend(); policy = false; arm.animate().cancel(); for (ArtworkLayer cover : covers) cover.animate().cancel(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); if (disc != null) refreshMotionPolicy(); }
    @Override protected void onVisibilityChanged(View view, int visibility) { super.onVisibilityChanged(view, visibility); if (disc != null) refreshMotionPolicy(); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        int size = Math.round(Math.min(getMeasuredWidth(), getMeasuredHeight() * 1.12f) * .85f);
        disc.measure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY));
        arm.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        float w = getWidth(), h = getHeight();
        cx = w * .47f; cy = h * .51f; radius = Math.min(w, h * 1.12f) * .425f;
        disc.layout(Math.round(cx - radius), Math.round(cy - radius), Math.round(cx + radius), Math.round(cy + radius));
        disc.setPivotX(disc.getWidth() / 2f); disc.setPivotY(disc.getHeight() / 2f);
        arm.layout(0, 0, getWidth(), getHeight());
        float px = w * .87f, py = h * .16f;
        arm.setPivotX(px); arm.setPivotY(py);
        double parked = Math.atan2(h * .80f - py, w * .92f - px);
        double active = Math.atan2(cy + radius * .40f - py, cx + radius * .64f - px);
        engaged = (float)Math.toDegrees(active - parked);
        if (changed) { moveArm(); invalidate(); }
    }
    @Override protected void onDraw(Canvas canvas) {
        shadow.setColor(Color.argb(22, 53, 41, 28)); canvas.drawOval(cx - radius, cy - radius + 12, cx + radius, cy + radius + 12, shadow);
        shadow.setColor(appearance==null?Color.rgb(185,177,158):appearance.track()); canvas.drawCircle(cx, cy, radius + 5, shadow);
    }

    private static class VectorLayer extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        VectorLayer(Context c) { super(c); }
        void fill(int color) { p.setShader(null); p.setColor(color); p.setStyle(Paint.Style.FILL); }
    }
    private static final class PlatterLayer extends VectorLayer {
        int accent=Color.rgb(173,83,56);
        private RadialGradient gradient;
        private final RectF gloss = new RectF();
        private int[] colors={0xff6fc2be,0xff798dff,0xfff08ac8};private Shader labelGradient;
        void setColors(int[] value){colors=value;accent=value[0];labelGradient=new LinearGradient(0,0,Math.max(1,getWidth()),Math.max(1,getHeight()),colors,null,Shader.TileMode.CLAMP);invalidate();}
        PlatterLayer(Context c) { super(c); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            setColors(colors);
            float radius = Math.min(w, h) / 2f;
            gradient = new RadialGradient(w / 2f - radius * .2f, h / 2f - radius * .3f, radius * 1.4f,
                new int[]{Color.rgb(55,55,52), Color.rgb(22,23,23), Color.rgb(9,10,10)}, new float[]{0,.55f,1}, Shader.TileMode.CLAMP);
            gloss.set(w / 2f - radius * .97f, h / 2f - radius * .97f, w / 2f + radius * .97f, h / 2f + radius * .97f);
        }
        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, radius = Math.min(getWidth(), getHeight()) / 2f;
            fill(Color.BLACK); p.setShader(gradient); c.drawCircle(cx, cy, radius, p); p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(.7f, radius * .003f));
            for (int i = 0; i < 43; i++) { p.setColor(i % 4 == 0 ? Color.argb(67,139,139,128) : Color.argb(38,144,144,130)); c.drawCircle(cx,cy,radius * (.36f + i * .014f),p); }
            p.setStrokeWidth(radius * .55f); p.setColor(Color.argb(14,255,255,240)); c.drawArc(gloss,218,35,false,p); c.drawArc(gloss,40,24,false,p);
            fill(accent); p.setShader(labelGradient); c.drawCircle(cx,cy,radius * .33f,p);p.setShader(null);
        }
    }
    private static final class ArtworkLayer extends VectorLayer {
        private Bitmap image;
        private final Path clip = new Path();
        private final Rect source = new Rect();
        private final RectF destination = new RectF();
        ArtworkLayer(Context c) { super(c); p.setTypeface(Typeface.create("sans-serif-medium", 0)); p.setTextAlign(Paint.Align.CENTER); }
        void setArtwork(Bitmap value) { image = value; invalidate(); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float cx = w / 2f, cy = h / 2f, label = Math.min(w, h) * .5f * .33f;
            clip.reset(); clip.addCircle(cx, cy, label * .83f, Path.Direction.CW);
            destination.set(cx - label * .83f, cy - label * .83f, cx + label * .83f, cy + label * .83f);
        }
        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, radius = Math.min(getWidth(), getHeight()) / 2f, label = radius * .33f;
            if (image != null && !image.isRecycled()) {
                int size = Math.min(image.getWidth(), image.getHeight());
                source.set((image.getWidth()-size)/2,(image.getHeight()-size)/2,(image.getWidth()+size)/2,(image.getHeight()+size)/2);
                c.save(); c.clipPath(clip); fill(Color.WHITE); c.drawBitmap(image,source,destination,p); c.restore();
            }
            fill(Color.rgb(221,211,190)); c.drawCircle(cx,cy,radius * .035f,p);
            fill(Color.rgb(18,22,22)); c.drawCircle(cx,cy,radius * .017f,p);
        }
    }
    private static final class ArmLayer extends VectorLayer {
        private final Path path = new Path();
        ArmLayer(Context c) { super(c); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float px=w * .87f, py=h * .16f, tx=w * .92f, ty=h * .80f;
            path.reset(); path.moveTo(px,py); path.lineTo(px-(px-tx)*.32f,py+(ty-py)*.55f); path.lineTo(tx,ty);
        }
        @Override protected void onDraw(Canvas c) {
            float w=getWidth(), h=getHeight(), radius=Math.min(w,h * 1.12f) * .425f, px=w * .87f, py=h * .16f, tx=w * .92f, ty=h * .80f;
            fill(Color.argb(28,39,33,22)); c.drawCircle(px+3,py+5,radius * .095f,p);
            fill(Color.rgb(100,105,100)); c.drawCircle(px,py,radius * .092f,p);
            fill(Color.rgb(208,205,192)); c.drawCircle(px,py,radius * .061f,p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(Color.argb(45,19,22,19)); p.setStrokeWidth(radius * .05f); c.save(); c.translate(3,5); c.drawPath(path,p); c.restore();
            p.setColor(Color.rgb(121,126,120)); p.setStrokeWidth(radius * .043f); c.drawPath(path,p);
            p.setColor(Color.rgb(236,232,213)); p.setStrokeWidth(radius * .018f); c.drawPath(path,p);
            fill(Color.rgb(49,57,51)); c.save(); c.rotate(-10,tx,ty); c.drawRoundRect(tx-radius * .045f,ty-radius * .01f,tx+radius * .045f,ty+radius * .11f,radius * .012f,radius * .012f,p);
            fill(Color.rgb(178,91,61)); c.drawRect(tx-radius * .038f,ty+radius * .085f,tx+radius * .038f,ty+radius * .105f,p); c.restore();
        }
    }
}
