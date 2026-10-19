package in.sarkarialert.app;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

/**
 * YouTube-style push-on-connectivity: no always-on service.
 * The system wakes our (already running, otherwise idle) process only when
 * internet becomes available; we fetch immediately and go back to sleep.
 * Zero work, zero battery while the phone is offline.
 */
public final class NetWatcher {

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static boolean registered = false;
    private static boolean refreshing = false;

    /** UI (if visible) is notified exactly when a fetch cycle completes */
    public static volatile Runnable uiListener;

    private NetWatcher() {}

    /** idempotent: safe to call from activity, receiver, job — registers once per process */
    public static synchronized void ensureRegistered(final Context context) {
        if (registered) return;
        final Context app = context.getApplicationContext();
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        try {
            ConnectivityManager.NetworkCallback cb = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    // brief settle so the network is usable, then one-shot fetch
                    UI.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            refreshOnce(app);
                        }
                    }, 1500);
                }
            };
            cm.registerDefaultNetworkCallback(cb);
            registered = true;
        } catch (Exception ignored) {
            // very old device or callback refused: JobScheduler + manual refresh still cover us
        }
    }

    /**
     * one-shot guarded fetch. Also invoked by:
     *  - ConnectivityAction broadcast (pre-N fallback path in BootReceiver)
     *  - the battery-friendly JobScheduler backup
     *  - the Refresh button
     */
    public static synchronized void refreshOnce(final Context context) {
        if (refreshing) return;
        refreshing = true;
        final Context app = context.getApplicationContext();
        SiteMonitor.refreshAll(app, UI, new SiteMonitor.Callback() {
            @Override
            public void onDone(int added, int total, int ok, java.util.List<Alert> fresh) {
                refreshing = false;
                if (added > 0 && fresh != null) {
                    int shown = 0;
                    for (Alert a : fresh) {
                        if (shown >= 3) break;
                        NotificationUtil.notifyNewAlert(app, a);
                        shown++;
                    }
                }
                Runnable r = uiListener;
                uiListener = null;
                if (r != null) UI.post(r);
            }
        });
    }

    /** best-effort idle/charging check used by the scheduled job */
    public static boolean isCharging(Context ctx) {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= 23) {
            return pm.isDeviceIdleMode() == false; // not in Doze: fine to run
        }
        return true;
    }
}
