package in.sarkarialert.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.util.List;

public class DailyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context ctx, Intent intent) {
        if (!Prefs.dailyEnabled(ctx)) return;
        final Context app = ctx.getApplicationContext();
        FeedLoader.refresh(app, new Handler(Looper.getMainLooper()), new FeedLoader.Callback() {
            @Override
            public void onDone(int added, int sources, boolean anySuccess, List<Alert> fresh) {
                Db db = new Db(app);
                db.cleanup(120);
                AlarmScheduler.scheduleDaily(app);
                if (added > 0 && Prefs.dailyEnabled(app)) {
                    NotificationUtil.notify(app, NotificationUtil.CH_DAILY,
                            app.getString(R.string.notif_daily_title),
                            app.getString(R.string.notif_daily_text, added), 0);
                }
            }
        });
    }
}
