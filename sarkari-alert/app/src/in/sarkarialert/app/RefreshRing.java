package in.sarkarialert.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Circular refresh indicator: partial arc grows with pull progress,
 * switches to a smooth infinite spin while fetching.
 */
public class RefreshRing extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float pull;
    private boolean spinning;
    private ObjectAnimator spin;

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
        paint.setStrokeWidth(dp(2.6f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFFF4772E);
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    public void setPull(float p) {
        pull = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    public void setSpinning(boolean s) {
        if (s == spinning) return;
        spinning = s;
        if (s) {
            spin = ObjectAnimator.ofFloat(this, "rotation", 0f, 360f);
            spin.setDuration(850);
            spin.setRepeatCount(ValueAnimator.INFINITE);
            spin.setInterpolator(new LinearInterpolator());
            spin.start();
        } else {
            if (spin != null) spin.cancel();
            setRotation(0);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight()) - dp(4);
        if (size <= 0) return;
        RectF oval = new RectF(dp(2), dp(2), dp(2) + size, dp(2) + size);
        float sweep = spinning ? 280f : 18f + pull * 262f;
        canvas.drawArc(oval, -90f, sweep, false, paint);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
