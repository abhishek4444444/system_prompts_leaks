package in.sarkarialert.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;

/**
 * One-time creative welcome: staggered fade+rise of icon, greeting,
 * developer credit line, subtitle and CTA. Shown only on first launch.
 */
public class WelcomeActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Prefs.firstRun(this)) {
            goMain();
            return;
        }
        setContentView(R.layout.activity_welcome);

        long delay = 120;
        delay += rise(findViewById(R.id.w_icon), delay);
        delay += rise(findViewById(R.id.w_hi), delay);
        delay += rise(findViewById(R.id.w_sub), delay);
        rise(findViewById(R.id.w_cta), delay);

        findViewById(R.id.w_cta).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setFirstRunDone(WelcomeActivity.this);
                goMain();
            }
        });
    }

    /** fade+rise animation, returns its duration for chaining */
    private long rise(View v, long startOffset) {
        AnimationSet set = new AnimationSet(true);
        set.setInterpolator(new DecelerateInterpolator());
        AlphaAnimation alpha = new AlphaAnimation(0f, 1f);
        TranslateAnimation up = new TranslateAnimation(0, 0, 40, 0);
        set.addAnimation(alpha);
        set.addAnimation(up);
        set.setDuration(460);
        set.setStartOffset(startOffset);
        set.setFillAfter(true);
        v.setVisibility(View.VISIBLE);
        v.startAnimation(set);
        return 460;
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
