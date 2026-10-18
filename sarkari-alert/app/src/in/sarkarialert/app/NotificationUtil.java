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

    private static void ensureChannels(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel r = new NotificationChannel(CH_REMINDERS,
                    ctx.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH);
            NotificationChannel d = new NotificationChannel(CH_DAILY,
                    ctx.getString(R.string.channel_daily), NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(r);
            nm.createNotificationChannel(d);
        }
    }

    public static void notify(Context ctx, String channel, String title, String text, long alertId) {
        ensureChannels(ctx);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(ctx, channel);
        } else {
            b = new Notification.Builder(ctx);
            b.setPriority(Notification.PRIORITY_HIGH);
        }
        Intent open;
        if (alertId > 0) {
            open = new Intent(ctx, DetailActivity.class);
            open.putExtra("id", alertId);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } else {
            open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        PendingIntent pi = PendingIntent.getActivity(ctx, (int) alertId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true);
        nm.notify((int) (alertId > 0 ? alertId : 1000 + System.currentTimeMillis() % 100000), b.build());
    }
}
