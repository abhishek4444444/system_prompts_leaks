package in.sarkarialert.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        long id = intent.getLongExtra("id", -1);
        if (id <= 0) return;
        Db db = new Db(ctx);
        Alert a = db.get(id);
        if (a == null || !a.saved) return;
        int d = AlarmScheduler.daysUntil(a.lastDate);
        String text;
        if (d == 0) {
            text = ctx.getString(R.string.notif_reminder_today, a.title);
        } else if (d > 0) {
            text = ctx.getString(R.string.notif_reminder_days, a.title, d);
        } else {
            return;
        }
        NotificationUtil.notify(ctx, NotificationUtil.CH_REMINDERS,
                ctx.getString(R.string.reminder_title), text, a.id);
    }
}
