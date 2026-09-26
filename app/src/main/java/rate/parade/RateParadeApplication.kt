package rate.parade

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.job.JobParameters
import android.content.ComponentName
import java.util.concurrent.Executors

class RateParadeApplication : Application() {
    val storageExecutor = Executors.newSingleThreadExecutor()
    private val networkExecutor = Executors.newSingleThreadExecutor()
    val store: LocalStore by lazy { LocalStore(noBackupFilesDir) }
    val repository: RateRepository by lazy { RateRepository(store, Frankfurter(), networkExecutor) }
    override fun onCreate() {
        super.onCreate()
        val scheduler = getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(1) == null) {
            scheduler.schedule(JobInfo.Builder(1, ComponentName(this, RefreshJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(REFRESH_INTERVAL)
                .build())
        }
    }
}

class RefreshJob : JobService() {
    private var active: JobParameters? = null
    override fun onStartJob(params: JobParameters): Boolean {
        active = params
        val app = application as RateParadeApplication
        app.storageExecutor.execute { app.repository.refresh(force = true).thenAccept {
            android.os.Handler(mainLooper).post {
                if (active === params) {
                    active = null
                    // A failed cycle ends here. The next periodic run/foreground/manual refresh retries.
                    jobFinished(params, false)
                }
            }
        } }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean {
        active = null
        return false
    }
}
