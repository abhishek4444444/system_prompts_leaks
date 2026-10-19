package in.sarkarialert.app;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Foreground live monitor: keeps polling feeds every few minutes and fires
 * an instant notification the moment a new government job, form or scheme
 * appears. Also watches connectivity — the second internet comes back,
 * a refresh is triggered immediately (no waiting for the next tick).
 */
public class MonitorService extends Service {

    private static final long POLL_MINUTES = 5;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService executor;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean refreshing = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationUtil.ensureChannels(this);
        startForeground(NotificationUtil.MONITOR_NOTIFICATION_ID,
                NotificationUtil.buildMonitorNotification(this));

        executor = Executors.newSingleThreadScheduledExecutor();
        // first check right away, then every POLL_MINUTES
        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                refreshNow();
            }
        }, 0, POLL_MINUTES, TimeUnit.MINUTES);

        registerNetworkWatcher();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (executor != null) executor.shutdownNow();
        if (networkCallback != null) {
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                cm.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    /** instant refresh as soon as internet is available */
    private void registerNetworkWatcher() {
        final ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                // small settle delay so the connection is actually usable
                ui.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refreshNow();
                    }
                }, 2000);
            }
        };
        try {
            cm.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception e) {
            // fall back silently; periodic polling still covers us
        }
    }

    private void refreshNow() {
        if (refreshing) return;
        refreshing = true;
        final Context app = getApplicationContext();
        FeedLoader.refresh(app, ui, new FeedLoader.Callback() {
            @Override
            public void onDone(int added, int sources, boolean anySuccess, List<Alert> fresh) {
                refreshing = false;
                if (added > 0 && fresh != null) {
                    int shown = 0;
                    for (Alert a : fresh) {
                        if (shown >= 3) break; // don't spam: bundle rest into daily digest
                        NotificationUtil.notifyNewAlert(app, a);
                        shown++;
                    }
                }
            }
        });
    }
}
