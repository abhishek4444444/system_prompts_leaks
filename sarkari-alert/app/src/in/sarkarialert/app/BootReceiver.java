package in.sarkarialert.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        Context app = ctx.getApplicationContext();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)) {
            AlarmScheduler.rescheduleAll(app);
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)
                || "android.net.conn.CONNECTIVITY_CHANGE".equals(a)) {
            NetWatcher.ensureRegistered(app);
            JobSchedulerUtil.schedule(app);
            NetWatcher.refreshOnce(app);
        }
    }
}
