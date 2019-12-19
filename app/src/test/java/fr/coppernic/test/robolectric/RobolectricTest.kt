package fr.coppernic.test.robolectric

import fr.bipi.tressence.console.SystemLogTree
import org.awaitility.Awaitility.await
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean


/**
 * Base class extended by every Robolectric test in this project.
 * <p>
 * Robolectric tests are done in a single thread !
 */
@RunWith(RobolectricTestRunner::class)
abstract class RobolectricTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            //Configure robolectric
            Timber.plant(SystemLogTree())
            ShadowLog.stream = System.out
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            Timber.uprootAll()
        }
    }

    private val unblock = AtomicBoolean(false)

    fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

    }

    fun unblock() {
        unblock.set(true)
    }

    fun block() {
        unblock.set(false)
        await().untilTrue(unblock)
    }
}
