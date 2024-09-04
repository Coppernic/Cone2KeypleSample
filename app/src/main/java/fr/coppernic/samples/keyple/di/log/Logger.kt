package fr.coppernic.samples.keyple.di.log

import org.koin.core.KoinApplication
import org.koin.core.logger.KOIN_TAG
import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import org.koin.core.logger.MESSAGE
import timber.log.Timber


class TimberLogger(level: Level = Level.INFO) : Logger(level) {

    override fun log(level: Level, msg: MESSAGE) {
        if (this.level <= level) {
            logOnLevel(msg)
        }
    }

    private fun logOnLevel(msg: MESSAGE) {
        when (this.level) {
            Level.DEBUG -> Timber.tag(KOIN_TAG).d(msg)
            Level.INFO -> Timber.tag(KOIN_TAG).i(msg)
            Level.ERROR -> Timber.tag(KOIN_TAG).e(msg)
            Level.NONE -> {
            }
        }
    }
}

/**
 * Setup Android Logger for Koin
 * @param level
 */
fun KoinApplication.timberLogger(
    level: Level = Level.INFO
): KoinApplication {
    logger(TimberLogger(level))
    return this
}
