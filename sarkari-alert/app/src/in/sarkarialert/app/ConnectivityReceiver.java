package in.sarkarialert.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * Legacy safety net (pre-Android-N devices ignore default network callbacks
 * from a dead process): fires a single guarded fetch when connectivity is
 * regained. On modern Android the manifest broadcast doesn't fire, and the
 * live NetWatcher callback covers it instead.
 */
public class ConnectivityReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        ConnectivityManager cm = (ConnectivityManager)
                ctx.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        NetworkInfo info = cm.getActiveNetworkInfo();
        boolean up = info != null && info.isConnected();
        if (up) {
            NetWatcher.ensureRegistered(ctx);
            JobSchedulerUtil.schedule(ctx);
            NetWatcher.refreshOnce(ctx);
        }
    }
}
