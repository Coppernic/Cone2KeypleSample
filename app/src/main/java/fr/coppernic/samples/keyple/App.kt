package fr.coppernic.samples.keyple

import android.app.Application
import android.util.Log
import fr.bipi.tressence.common.filters.TagFilter
import fr.bipi.tressence.file.FileLoggerTree
import fr.bipi.tressence.sentry.SentryBreadcrumbTree
import fr.bipi.tressence.sentry.SentryEventTree
import fr.coppernic.sdk.utils.helpers.OsHelper
import fr.coppernic.samples.keyple.BuildConfig.DEBUG
import fr.coppernic.samples.keyple.di.log.timberLogger
import fr.coppernic.samples.keyple.di.modules.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class App : Application() {

    companion object {
        @JvmStatic
        lateinit var fileTree: FileLoggerTree
    }

    override fun onCreate() {
        super.onCreate()
        setupDi()
        setupLog()
    }

    private fun setupDi() {
        startKoin {
            timberLogger()
            androidContext(this@App)
            modules(appModule)
        }
    }

    private fun setupLog() {

        Timber.plant(Timber.DebugTree())

        // File Log
        fileTree = FileLoggerTree.Builder()
                .withDirName(filesDir.absolutePath)
                .withMinPriority(Log.VERBOSE).build()
        Timber.plant(fileTree)

        // Sentry
        if (!DEBUG && !OsHelper.isRobolectric()) {
            // Do not send log to itself
            Timber.plant(SentryBreadcrumbTree(Log.VERBOSE, filter = TagFilter("^(?!.*(Sentry|App)).*")))
            Timber.plant(SentryEventTree(Log.ASSERT, filter = TagFilter("^(?!.*(Sentry|App)).*")))
        }
    }
}
