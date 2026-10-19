package in.sarkarialert.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * Material-style circular refresh indicator.
 *
 * PULL: an arrow-tipped arc grows and rotates with the drag; pops to a full
 *       ring (armed) when the release threshold is crossed.
 * SPIN: the classic indeterminate "swoosh" — the arc opens to 270 degrees
 *       and closes again while the start angle chases around the circle.
 * DONE: finishSpin() completes the circle, then shrinks and fades away.
 */
public class RefreshRing extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Path arrow = new Path();

    private float pull;              // 0..1 drag progress
    private boolean armed;
    private boolean spinning;
    private float spinStart = -90f;  // animated start angle in spin mode
    private float spinSweep = 24f;   // animated sweep in spin mode
    private float outScale = 1f;     // finish animation
    private float outAlpha = 1f;
    private ValueAnimator spinAnim;

    public RefreshRing(Context c) {
        super(c);
        init();
    }

    public RefreshRing(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.8f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFFF4772E);
        setVisibility(GONE); // nothing drawn until actually used — no stray arc
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    /** Drag progress 0..1. Hitting 1 arms the ring with a scale pop. */
    public void setPull(float p) {
        pull = Math.max(0f, Math.min(1f, p));
        if (pull >= 1f && !armed) {
            armed = true;
            pop();
        } else if (armed && pull < 0.92f) {
            armed = false; // hysteresis: no pop-flicker around the threshold
        }
        invalidate();
    }

    /** Backwards-compatible toggle used by the small header button. */
    public void setSpinning(boolean s) {
        if (s) startSpin();
        else stopSpin();
    }

    public boolean isSpinning() {
        return spinning;
    }

    /** Start the indeterminate swoosh. */
    public void startSpin() {
        armed = false;
        spinning = true;
        outScale = 1f;
        outAlpha = 1f;
        setAlpha(1f);
        setScaleX(1f);
        setScaleY(1f);
        setVisibility(VISIBLE);
        if (spinAnim != null) spinAnim.cancel();
        spinAnim = ValueAnimator.ofFloat(0f, 1f);
        spinAnim.setDuration(1100);
        spinAnim.setRepeatCount(ValueAnimator.INFINITE);
        spinAnim.setInterpolator(new LinearInterpolator());
        spinAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float t = (Float) a.getAnimatedValue();
                spinStart = -90f + t * 360f;
                float grow = t < 0.5f ? smooth(t / 0.5f)
                        : smooth(1f - (t - 0.5f) / 0.5f);
                spinSweep = 24f + grow * 246f; // 24 -> 270 -> 24 per revolution
                invalidate();
            }
        });
        spinAnim.start();
    }

    /** Stop instantly (header button). */
    public void stopSpin() {
        spinning = false;
        if (spinAnim != null) {
            spinAnim.cancel();
            spinAnim = null;
        }
        setVisibility(GONE);
        setRotation(0f);
    }

    /** Graceful finish: complete the circle, shrink + fade, hide. */
    public void finishSpin() {
        if (!spinning) return;
        spinning = false;
        if (spinAnim != null) {
            spinAnim.cancel();
            spinAnim = null;
        }
        ValueAnimator close = ValueAnimator.ofFloat(spinSweep, 360f);
        close.setDuration(170);
        close.setInterpolator(new DecelerateInterpolator(1.2f));
        close.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                spinSweep = (Float) a.getAnimatedValue();
                spinStart = -90f;
                invalidate();
            }
        });
        ValueAnimator out = ValueAnimator.ofFloat(0f, 1f);
        out.setDuration(230);
        out.setInterpolator(new DecelerateInterpolator(1.4f));
        out.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float f = (Float) a.getAnimatedValue();
                outScale = 1f - 0.45f * f;
                outAlpha = 1f - f;
                setScaleX(outScale);
                setScaleY(outScale);
                setAlpha(outAlpha);
                invalidate();
            }
        });
        AnimatorSet set = new AnimatorSet();
        set.playSequentially(close, out);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                setVisibility(GONE);
                setAlpha(1f);
                setScaleX(1f);
                setScaleY(1f);
                outScale = 1f;
                outAlpha = 1f;
                spinSweep = 24f;
            }
        });
        set.start();
    }

    private void pop() {
        ValueAnimator v = ValueAnimator.ofFloat(1f, 1.16f, 1f);
        v.setDuration(170);
        v.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float s = (Float) a.getAnimatedValue();
                setScaleX(s);
                setScaleY(s);
            }
        });
        v.start();
    }

    private static float smooth(float x) {
        return x * x * (3f - 2f * x);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getVisibility() != VISIBLE) return;
        float s = Math.min(getWidth(), getHeight());
        float inset = dp(3.4f);
        if (s - inset * 2f <= 0f) return;
        float cx = s / 2f;
        float r = s / 2f - inset;
        oval.set(cx - r, cx - r, cx + r, cx + r);
        paint.setAlpha((int) (outAlpha * 255f));
        canvas.save();
        canvas.scale(outScale, outScale, cx, cx);
        if (spinning) {
            canvas.drawArc(oval, spinStart, spinSweep, false, paint);
        } else {
            float sweep = 20f + pull * 280f;  // 20 -> 300 degrees
            float start = -90f + pull * 250f; // whole arc rotates while dragging
            canvas.drawArc(oval, start, sweep, false, paint);
            if (pull < 0.96f) { // arrowhead melts away at full pull
                double rad = Math.toRadians(start + sweep);
                float ax = cx + (float) Math.cos(rad) * r;
                float ay = cx + (float) Math.sin(rad) * r;
                float len = dp(4.4f) * (1f - pull);
                float wid = dp(3.6f) * (1f - pull);
                arrow.reset();
                arrow.moveTo(ax + (float) Math.cos(rad) * len,
                        ay + (float) Math.sin(rad) * len);
                arrow.lineTo(ax + (float) Math.cos(rad + Math.PI / 2f) * wid,
                        ay + (float) Math.sin(rad + Math.PI / 2f) * wid);
                arrow.lineTo(ax + (float) Math.cos(rad - Math.PI / 2f) * wid,
                        ay + (float) Math.sin(rad - Math.PI / 2f) * wid);
                arrow.close();
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(arrow, paint);
                paint.setStyle(Paint.Style.STROKE);
            }
        }
        canvas.restore();
        paint.setAlpha(255);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
