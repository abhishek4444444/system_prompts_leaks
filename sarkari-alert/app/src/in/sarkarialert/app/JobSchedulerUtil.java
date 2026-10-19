package in.sarkarialert.app;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

public class JobSchedulerUtil {
    private static final int JOB_ID = 4242;

    /** schedule a periodic system job: cheap, system-managed, battery-safe */
    public static void schedule(Context ctx) {
        if (Build.VERSION.SDK_INT < 21) return;
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        ComponentName cn = new ComponentName(ctx, BackupJob.class);
        JobInfo.Builder b = new JobInfo.Builder(JOB_ID, cn)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setPeriodic(15 * 60 * 1000L);
        try {
            js.schedule(b.build());
        } catch (Exception ignored) { }
    }
}
