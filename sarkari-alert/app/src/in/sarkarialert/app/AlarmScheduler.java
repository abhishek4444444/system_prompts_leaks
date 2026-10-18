package in.sarkarialert.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.List;

public class AlarmScheduler {

    /** schedule deadline reminders (3 days and 1 day before) for a saved alert */
    public static void scheduleFor(Context ctx, Alert a) {
        if (a == null || a.lastDate == null || a.lastDate.length() < 10) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        int d = daysUntil(a.lastDate);
        if (d <= 0) return; // already past or today; skip
        int[][] slots = {{3, 1}, {1, 2}};
        for (int[] slot : slots) {
            int days = slot[0];
            if (d > days) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_YEAR, d - days);
                cal.set(Calendar.HOUR_OF_DAY, 9);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                long at = cal.getTimeInMillis();
                PendingIntent pi = pending(ctx, a.id, slot[1]);
                if (Build.VERSION.SDK_INT >= 23) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
                }
            }
        }
    }

    public static void cancelFor(Context ctx, long alertId) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending(ctx, alertId, 1));
        am.cancel(pending(ctx, alertId, 2));
    }

    private static PendingIntent pending(Context ctx, long alertId, int slot) {
        Intent i = new Intent(ctx, ReminderReceiver.class);
        i.putExtra("id", alertId);
        return PendingIntent.getBroadcast(ctx, (int) (alertId * 10 + slot), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** reschedule all saved alerts + daily sync; call on boot */
    public static void rescheduleAll(Context ctx) {
        Db db = new Db(ctx);
        List<Alert> saved = db.savedAlerts();
        for (Alert a : saved) scheduleFor(ctx, a);
        scheduleDaily(ctx);
    }

    public static void scheduleDaily(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 8);
        cal.set(Calendar.MINUTE, 30);
        cal.set(Calendar.SECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        Intent i = new Intent(ctx, DailyReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 99, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pi);
    }

    public static void cancelDaily(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ctx, DailyReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 99, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }

    /** days from today until ISO date; negative if past */
    public static int daysUntil(String iso) {
        if (iso == null || iso.length() < 10) return Integer.MIN_VALUE;
        try {
            String[] p = iso.substring(0, 10).split("-");
            Calendar due = Calendar.getInstance();
            due.clear();
            due.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            Calendar now = Calendar.getInstance();
            Calendar today = Calendar.getInstance();
            today.clear();
            today.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
            return (int) ((due.getTimeInMillis() - today.getTimeInMillis()) / 86400000L);
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    /** ISO date -> "19 Oct 2026" style Hindi-ish display */
    public static String prettyDate(String iso) {
        if (iso == null || iso.length() < 10) return iso == null ? "" : iso;
        try {
            String[] p = iso.substring(0, 10).split("-");
            int y = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]), d = Integer.parseInt(p[2]);
            String[] mon = {"जन", "फ़र", "मार्च", "अप्रै", "मई", "जून", "जुल", "अग", "सित", "अक्टू", "नव", "दिस"};
            return d + " " + mon[m - 1] + " " + y;
        } catch (Exception e) {
            return iso;
        }
    }
}
