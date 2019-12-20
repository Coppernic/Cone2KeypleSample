package fr.coppernic.samples.cone2.keyple.home

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import fr.coppernic.samples.cone2.keyple.R
import kotlinx.android.synthetic.main.activity_main.*
import org.eclipse.keyple.calypso.command.po.parser.ReadDataStructure
import org.eclipse.keyple.calypso.command.po.parser.ReadRecordsRespPars
import org.eclipse.keyple.calypso.command.sam.SamRevision.C1
import org.eclipse.keyple.calypso.command.sam.SamRevision.S1D
import org.eclipse.keyple.calypso.transaction.*
import org.eclipse.keyple.core.selection.SeSelection
import org.eclipse.keyple.core.seproxy.ChannelControl
import org.eclipse.keyple.core.seproxy.SeProxyService
import org.eclipse.keyple.core.seproxy.SeReader
import org.eclipse.keyple.core.seproxy.SeSelector
import org.eclipse.keyple.core.seproxy.event.*
import org.eclipse.keyple.core.seproxy.exception.KeyplePluginInstantiationException
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderException
import org.eclipse.keyple.core.seproxy.protocol.SeCommonProtocols
import org.eclipse.keyple.core.util.ByteArrayUtil
import org.eclipse.keyple.plugin.android.cone2.Cone2Factory
import org.eclipse.keyple.plugin.android.cone2.Cone2Plugin
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean


class HomeActivity : AppCompatActivity() {

    companion object {
        const val LINE_SEPARATOR = "\n ---- \n"
    }

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

                (seReader as ObservableReader).startSeDetection(ObservableReader.PollingMode.REPEATING)
            }
            PluginEvent.EventType.READER_DISCONNECTED -> appendColoredText(tvLogs,
                    "Readers have been disconnected",
                    Color.BLUE)
        }
    }

    private val readerObserver = ObservableReader.ReaderObserver { readerEvent ->
        runOnUiThread {
            when (readerEvent.eventType) {
                ReaderEvent.EventType.TIMEOUT_ERROR -> TODO()
                ReaderEvent.EventType.SE_INSERTED -> {
                    appendColoredText(tvLogs,
                            R.string.smart_card_detected,
                            Color.GREEN)
                    appendColoredText(tvLogs,
                            LINE_SEPARATOR,
                            Color.BLACK)

                    runCalypsoTransaction()
                }
                ReaderEvent.EventType.SE_MATCHED -> {
                    appendColoredText(tvLogs,
                            R.string.se_matched,
                            Color.GREEN)
                    appendColoredText(tvLogs,
                            LINE_SEPARATOR,
                            Color.BLACK)

                    runCalypsoTransaction()
                }
                ReaderEvent.EventType.SE_REMOVED -> {
                    appendColoredText(tvLogs,
                            R.string.smart_card_removed,
                            Color.RED)
                    appendColoredText(tvLogs,
                            LINE_SEPARATOR,
                            Color.BLACK)
                }
            }
        }
    }



    private fun runCalypsoTransaction() {
        Timber.d("runCalypsoTransation")

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

        // Initializes the selection process
        val seSelection = SeSelection()

        seSelection.prepareSelection(poSelectionRequest)

        val selectionsResult = seSelection.processExplicitSelection(seReader)

        if (selectionsResult.hasActiveSelection()) {
            Timber.d("selectionsResult.hasActiveSelection")
            val matchingSelection = selectionsResult.activeSelection

            tvLogs.append("The selection of the PO has succeeded.\n")
            /* Go on with the reading of the first record of the EventLog file */
            tvLogs.append(" ---- \n")
            tvLogs.append("2nd PO exchange: \n")
            tvLogs.append("open and close a secure session to perform authentication.\n")
            tvLogs.append(" ---- \n")

            val calypsoPo = matchingSelection.matchingSe as CalypsoPo

            val poTransaction = PoTransaction(PoResource(seReader, calypsoPo),
                    samResource, SecuritySettings())

            /*
                             * Prepare the reading order and keep the associated parser for later use once the
                             * transaction has been processed.
                             */
            val readEventLogParserIndex = poTransaction.prepareReadRecordsCmd(
                    CalypsoClassicInfo.SFI_EventLog, ReadDataStructure.SINGLE_RECORD_DATA,
                    CalypsoClassicInfo.RECORD_NUMBER_1,
                    String.format("EventLog (SFI=%02X, recnbr=%d))",
                            CalypsoClassicInfo.SFI_EventLog,
                            CalypsoClassicInfo.RECORD_NUMBER_1))

            /*
                             * Open Session for the debit key
                             */
            var poProcessStatus = poTransaction.processOpening(
                    PoTransaction.ModificationMode.ATOMIC,
                    PoTransaction.SessionAccessLevel.SESSION_LVL_DEBIT, 0.toByte(), 0.toByte())

            check(poProcessStatus) { "processingOpening failure.\n" }

            if (!poTransaction.wasRatified()) {
                appendColoredText(tvLogs,
                        "Previous Secure Session was not ratified.\n",
                        Color.RED)
            }
            /*
                             * Prepare the reading order and keep the associated parser for later use once the
                             * transaction has been processed.
                             */
            val readEventLogParserIndexBis = poTransaction.prepareReadRecordsCmd(
                    CalypsoClassicInfo.SFI_EventLog, ReadDataStructure.SINGLE_RECORD_DATA,
                    CalypsoClassicInfo.RECORD_NUMBER_1,
                    String.format("EventLog (SFI=%02X, recnbr=%d))",
                            CalypsoClassicInfo.SFI_EventLog,
                            CalypsoClassicInfo.RECORD_NUMBER_1))

            poProcessStatus = poTransaction.processPoCommandsInSession()

            /*
                             * Retrieve the data read from the parser updated during the transaction process
                             */
            val eventLog = (poTransaction.getResponseParser(readEventLogParserIndexBis) as ReadRecordsRespPars).records[CalypsoClassicInfo.RECORD_NUMBER_1.toInt()]

            /* Log the result */
            tvLogs.append("EventLog file data: " + ByteArrayUtil.toHex(eventLog) + "\n")

            check(poProcessStatus) { "processPoCommandsInSession failure.\n" }

            /*
             * Closes the Secure Session.
             */
            tvLogs.append("PO Calypso session: Closing\n")


            /*
             * A ratification command will be sent (CONTACTLESS_MODE).
             */
            poProcessStatus = poTransaction.processClosing(ChannelControl.CLOSE_AFTER)

            check(poProcessStatus) { "processClosing failure.\n" }

            tvLogs.append(" ---- \n")
            tvLogs.append("End of the Calypso PO processing.\n")
            tvLogs.append(" ---- \n")
        } else run {
            appendColoredText(tvLogs,
                    "The selection of the PO has failed.",
                    Color.RED)
        }

        // Reader is now in card detection mode
        Timber.d("notifySeProcessed")
        (seReader as ObservableReader).notifySeProcessed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Manages power switch
        swPower.setOnCheckedChangeListener { _, isChecked ->
            powerReaders(isChecked)
        }

        // Starts Keyple
        try {
            createPlugin(Cone2Factory())
        } catch (e: KeyplePluginInstantiationException) {
            appendColoredText(tvLogs, e.message!!, Color.RED)
        }

        floatingActionButton.setOnClickListener {
            //(seReader as ObservableReader).stopSeDetection()
            // Notifies the end of process
            Timber.d("notifySeProcessed")
            (seReader as ObservableReader).notifySeProcessed()
        }
    }

    override fun onStart() {
        super.onStart()

        plugin.addObserver(pluginObserver)
    }

    override fun onStop() {
        super.onStop()
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

    /**
     * Powers on/off readers
     */
    private fun powerReaders(on:Boolean) {
        (plugin as Cone2Plugin).power(this, on)
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

        val samSelector = SamSelector(C1, ".*", "Selection SAM C1")

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
