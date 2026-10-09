package dev.tossfront.record;

import android.app.job.*;

public final class HistoryCleanup extends JobService {
    @Override public boolean onStartJob(JobParameters parameters){RecordLibrary.get(this).prune();return false;}
    @Override public boolean onStopJob(JobParameters parameters){return true;}
}
