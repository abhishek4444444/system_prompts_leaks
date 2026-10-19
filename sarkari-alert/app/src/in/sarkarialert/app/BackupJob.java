package in.sarkarialert.app;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;

/**
 * Battery-friendly backup net: JobScheduler only runs this when the system
 * deems it cheap (unmetered + idle window). It never keeps anything alive.
 */
public class BackupJob extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        NetWatcher.refreshOnce(this);
        jobFinished(params, false);
        return false;
    }
    @Override
    public boolean onStopJob(JobParameters params) { return false; }
}
