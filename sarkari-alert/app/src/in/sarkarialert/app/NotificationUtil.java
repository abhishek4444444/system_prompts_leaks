package in.sarkarialert.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class NotificationUtil {

    public static final String CH_REMINDERS = "reminders";
    public static final String CH_DAILY = "daily";
    public static final String CH_UPDATES = "updates";
    public static final String CH_MONITOR = "monitor";
    public static final int MONITOR_NOTIFICATION_ID = 42;

    public static void ensureChannels(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(CH_REMINDERS,
                    ctx.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH));
            nm.createNotificationChannel(new NotificationChannel(CH_DAILY,
                    ctx.getString(R.string.channel_daily), NotificationManager.IMPORTANCE_DEFAULT));
            nm.createNotificationChannel(new NotificationChannel(CH_UPDATES,
                    ctx.getString(R.string.channel_daily), NotificationManager.IMPORTANCE_HIGH));
            // silent persistent notification for the live monitor
            NotificationChannel mon = new NotificationChannel(CH_MONITOR,
                    ctx.getString(R.string.channel_monitor), NotificationManager.IMPORTANCE_LOW);
            mon.setShowBadge(false);
            nm.createNotificationChannel(mon);
        }
    }

    private static Notification.Builder base(Context ctx, String channel) {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(ctx, channel);
        }
        Notification.Builder b = new Notification.Builder(ctx);
        b.setPriority(Notification.PRIORITY_HIGH);
        return b;
    }

    private static PendingIntent openDetail(Context ctx, long alertId) {
        Intent open = new Intent(ctx, DetailActivity.class);
        open.putExtra("id", alertId);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, (int) alertId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** the silent, persistent "on watch" notification for the foreground service */
    public static Notification buildMonitorNotification(Context ctx) {
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 7, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return base(ctx, CH_MONITOR)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(ctx.getString(R.string.monitor_title))
                .setContentText(ctx.getString(R.string.monitor_text))
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    /** instant per-alert notification when something new drops */
    public static void notifyNewAlert(Context ctx, Alert a) {
        ensureChannels(ctx);
        String body = "";
        if (a.lastDate != null && a.lastDate.length() >= 10) {
            int d = AlarmScheduler.daysUntil(a.lastDate);
            body = d >= 0
                    ? ctx.getString(R.string.last_date_label, AlarmScheduler.prettyDate(a.lastDate))
                    : "";
        }
        if (body.length() == 0 && a.posts.length() > 0) body = a.posts;
        if (body.length() == 0 && a.org.length() > 0) body = a.org;
        if (body.length() == 0) body = ctx.getString(R.string.notif_fallback);
        Notification n = base(ctx, CH_UPDATES)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(a.title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(openDetail(ctx, a.id))
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_EVENT)
                .setAutoCancel(true)
                .build();
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify((int) (2000 + a.id % 100000), n);
    }

    public static void notify(Context ctx, String channel, String title, String text, long alertId) {
        ensureChannels(ctx);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        PendingIntent pi = alertId > 0 ? openDetail(ctx, alertId) : openMain(ctx);
        Notification n = base(ctx, channel)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify((int) (alertId > 0 ? alertId : 1000 + System.currentTimeMillis() % 100000), n);
    }

    private static PendingIntent openMain(Context ctx) {
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(ctx, 9, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
