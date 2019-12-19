package fr.coppernic.samples.cone2.keyple.home

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import fr.coppernic.samples.cone2.keyple.R
import kotlinx.android.synthetic.main.activity_main.*
import org.eclipse.keyple.calypso.command.sam.SamRevision.S1D
import org.eclipse.keyple.calypso.transaction.*
import org.eclipse.keyple.core.selection.SeSelection
import org.eclipse.keyple.core.seproxy.SeProxyService
import org.eclipse.keyple.core.seproxy.SeReader
import org.eclipse.keyple.core.seproxy.SeSelector
import org.eclipse.keyple.core.seproxy.event.*
import org.eclipse.keyple.core.seproxy.exception.KeyplePluginInstantiationException
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderException
import org.eclipse.keyple.core.seproxy.protocol.SeCommonProtocols
import org.eclipse.keyple.plugin.android.cone2.Cone2Factory
import org.eclipse.keyple.plugin.android.cone2.Cone2Plugin
import java.util.concurrent.atomic.AtomicBoolean


class HomeActivity : AppCompatActivity() {

    companion object {
        const val LINE_SEPARATOR = "\n ---- \n"
    }

    private val isHuntingForCard = AtomicBoolean()

    private lateinit var seProxyService: SeProxyService
    private lateinit var plugin: ObservablePlugin
    // RFID reader
    private lateinit var seReader: SeReader
    // SAM reader
    private lateinit var samReader: SeReader
    private lateinit var samResource: SamResource

    private val pluginObserver = ObservablePlugin.PluginObserver { pluginEvent ->
        when (pluginEvent.eventType) {

            PluginEvent.EventType.READER_CONNECTED -> {
                appendColoredText(tvLogs,
                        "Readers have been connected",
                        Color.BLUE)
                appendColoredText(tvLogs, "\n", Color.BLACK)
                try {
                    initReaders()
                } catch (ise: IllegalStateException) {
                                            appendColoredText(tvLogs,
                                                    ise.message!!,
                                                    Color.RED)
                }

                appendColoredText(tvLogs,
                        LINE_SEPARATOR,
                        Color.BLACK)
            }
            PluginEvent.EventType.READER_DISCONNECTED -> appendColoredText(tvLogs,
                    "Readers have been disconnected",
                    Color.BLUE)
        }
    }

    private val readerObserver = ObservableReader.ReaderObserver { readerEvent ->

        when (readerEvent.eventType) {
            ReaderEvent.EventType.TIMEOUT_ERROR -> TODO()
            ReaderEvent.EventType.SE_INSERTED -> {
                //(seReader as ObservableReader).stopSeDetection()
                (seReader as ObservableReader).notifySeProcessed()
                runOnUiThread { floatingActionButton.setImageResource(R.drawable.ic_play_arrow_primary_24dp) }
                isHuntingForCard.set(false)
                appendColoredText(tvLogs,
                        R.string.smart_card_detected,
                        Color.BLUE)
                appendColoredText(tvLogs,
                        "\n",
                        Color.BLACK)
                runCalypsoTransaction(readerEvent.defaultSelectionsResponse)
            }
            ReaderEvent.EventType.SE_MATCHED -> {
                //(seReader as ObservableReader).notifySeProcessed()
                (seReader as ObservableReader).stopSeDetection()
                appendColoredText(tvLogs,
                        R.string.se_matched,
                        Color.GREEN)
                appendColoredText(tvLogs,
                        LINE_SEPARATOR,
                        Color.BLACK)
            }
            ReaderEvent.EventType.SE_REMOVED -> appendColoredText(tvLogs,
                    R.string.smart_card_removed,
                    Color.RED)
        }
    }

    private fun preparePoSelection() : AbstractDefaultSelectionsRequest {
        // Initializes the selection process
        val seSelection = SeSelection()

        /*
         * Setting of an AID based selection of a Calypso REV3 PO
         *
         * Select the first application matching the selection AID whatever the SE communication
         * protocol keep the logical channel open after the selection
         */

        /*
         * Calypso selection: configures a PoSelectionRequest with all the desired attributes to
         * make the selection and read additional information afterwards
         */
        val AID = "A0000004040125090101"

        val poSelectionRequest = PoSelectionRequest(
                PoSelector(SeCommonProtocols.PROTOCOL_ISO14443_4,
                        null,
                        PoSelector.PoAidSelector(SeSelector.AidSelector.IsoAid(AID), PoSelector.InvalidatedPo.REJECT),
                            "AID: $AID")
        )

        seSelection.prepareSelection(poSelectionRequest)

        return seSelection.selectionOperation
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initTextView()

        // Starts Keyple
        try {
            createPlugin(Cone2Factory())
        } catch (e: KeyplePluginInstantiationException) {
            appendColoredText(tvLogs, e.message!!, Color.RED)
        }

        floatingActionButton.setOnClickListener {
            if (isHuntingForCard.get()) {
                floatingActionButton.setImageResource(R.drawable.ic_play_arrow_primary_24dp)
            } else {
                floatingActionButton.setImageResource(R.drawable.ic_stop_primary_24dp)
                //(seReader as ObservableReader).startSeDetection(ObservableReader.PollingMode.REPEATING)
                /* Set the default selection operation */
                (seReader as ObservableReader).setDefaultSelectionRequest(
                        preparePoSelection(),
                        ObservableReader.NotificationMode.MATCHED_ONLY,
                        ObservableReader.PollingMode.REPEATING)

            }

            isHuntingForCard.set(!isHuntingForCard.get())
        }
    }

    override fun onStart() {
        super.onStart()

        plugin.addObserver(pluginObserver)
        (plugin as Cone2Plugin).power(this, true)
    }

    override fun onStop() {
        super.onStop()
        (plugin as Cone2Plugin).power(this, false)
        plugin.removeObserver(pluginObserver)
        (seReader as ObservableReader).removeObserver(readerObserver)
    }

    /**
     * Creates keyple plugin
     * @throws KeyplePluginInstantiationException if instantiation failed
     */
    @Throws(KeyplePluginInstantiationException::class)
    private fun createPlugin(pluginFactory: Cone2Factory) {
        seProxyService = SeProxyService.getInstance()
        seProxyService.registerPlugin(pluginFactory)
        plugin = seProxyService.plugins.first() as ObservablePlugin
    }

    @Throws(IllegalStateException::class)
    private fun initReaders() {
        try {
            seReader = plugin.readers.last()
            (seReader as ObservableReader).addObserver(this@HomeActivity.readerObserver)
        } catch (e: KeypleReaderException) {
            e.printStackTrace()
        }

        /*
         * Get a SAM reader ready to work with Calypso PO. Use the getReader helper method from the
         * CalypsoUtilities class.
         */

        try {
            samReader = seProxyService.plugins.first().readers.first()
        } catch (e: KeypleReaderException) {
            e.printStackTrace()
        }

        /*
         * check the availability of the SAM doing a ATR based selection, open its physical and
         * logical channels and keep it open
         */
        val samSelection = SeSelection()

        val samSelector = SamSelector(S1D, ".*", "Selection SAM D6")

        /* Prepare selector, ignore AbstractMatchingSe here */
        samSelection.prepareSelection(SamSelectionRequest(samSelector))
        val calypsoSam: CalypsoSam

        try {
            calypsoSam = samSelection.processExplicitSelection(samReader)
                    .activeSelection.matchingSe as CalypsoSam
            check(calypsoSam.isSelected) { "Unable to open a logical channel for SAM!" }
        } catch (e: KeypleReaderException) {
            throw IllegalStateException("Reader exception: " + e.message)
        }

        samResource = SamResource(samReader, calypsoSam)
    }

    /**
     * Runs Calypso simple read transaction
     */
    private fun runCalypsoTransaction(defaultSelectionsResponse: AbstractDefaultSelectionsResponse) {
        appendColoredText(tvLogs, LINE_SEPARATOR, Color.BLACK)
    }

    /**
     * Initializes display
     */
    private fun initTextView() {
        tvLogs.text = ""// reset
        appendColoredText(tvLogs, R.string.waiting_for_a_smart_card, Color.BLUE)
        tvLogs.append(LINE_SEPARATOR)
    }

    /**
     * Appends to tv a textId colored according to the provided argument
     *
     * @param tv TextView
     * @param textId String resource id
     * @param color color value
     */
    private fun appendColoredText(tv: TextView, textId: Int, color: Int) {
        this.runOnUiThread {
            val start = tv.text.length
            tv.append(getText(textId))
            val end = tv.text.length

            val spannableText = tv.text as Spannable
            spannableText.setSpan(ForegroundColorSpan(color), start, end, 0)
        }
    }

    /**
     * Appends to tv a textId colored according to the provided argument
     *
     * @param tv TextView
     * @param text text
     * @param color color value
     */
    private fun appendColoredText(tv: TextView, text: String, color: Int) {
        this.runOnUiThread {
            val start = tv.text.length
            tv.append(text)
            val end = tv.text.length

            val spannableText = tv.text as Spannable
            spannableText.setSpan(ForegroundColorSpan(color), start, end, 0)
        }
    }
}
