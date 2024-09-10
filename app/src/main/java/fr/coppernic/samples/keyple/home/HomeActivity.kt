package fr.coppernic.samples.keyple.home

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import fr.coppernic.samples.keyple.R
import fr.coppernic.samples.keyple.databinding.ActivityMainBinding
import org.eclipse.keyple.calypso.command.po.parser.ReadDataStructure
import org.eclipse.keyple.calypso.command.po.parser.ReadRecordsRespPars
import org.eclipse.keyple.calypso.command.sam.SamRevision.C1
import org.eclipse.keyple.calypso.transaction.CalypsoPo
import org.eclipse.keyple.calypso.transaction.CalypsoSam
import org.eclipse.keyple.calypso.transaction.PoResource
import org.eclipse.keyple.calypso.transaction.PoSelectionRequest
import org.eclipse.keyple.calypso.transaction.PoSelector
import org.eclipse.keyple.calypso.transaction.PoTransaction
import org.eclipse.keyple.calypso.transaction.SamResource
import org.eclipse.keyple.calypso.transaction.SamSelectionRequest
import org.eclipse.keyple.calypso.transaction.SamSelector
import org.eclipse.keyple.calypso.transaction.SecuritySettings
import org.eclipse.keyple.core.selection.SeSelection
import org.eclipse.keyple.core.selection.SelectionsResult
import org.eclipse.keyple.core.seproxy.ChannelControl
import org.eclipse.keyple.core.seproxy.SeProxyService
import org.eclipse.keyple.core.seproxy.SeReader
import org.eclipse.keyple.core.seproxy.SeSelector
import org.eclipse.keyple.core.seproxy.event.AbstractDefaultSelectionsResponse
import org.eclipse.keyple.core.seproxy.event.ObservablePlugin
import org.eclipse.keyple.core.seproxy.event.ObservableReader
import org.eclipse.keyple.core.seproxy.event.PluginEvent
import org.eclipse.keyple.core.seproxy.event.ReaderEvent
import org.eclipse.keyple.core.seproxy.exception.KeyplePluginInstantiationException
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderException
import org.eclipse.keyple.core.seproxy.protocol.SeCommonProtocols
import org.eclipse.keyple.core.util.ByteArrayUtil
import org.eclipse.keyple.plugin.android.cone2.Cone2Factory
import org.eclipse.keyple.plugin.android.cone2.Cone2Plugin
import timber.log.Timber


class HomeActivity : AppCompatActivity() {

    companion object {
        const val LINE_SEPARATOR = "\n ---- \n"
    }

    private lateinit var seProxyService: SeProxyService
    private lateinit var plugin: ObservablePlugin
    // RFID reader
    private var seReader: SeReader? = null
    // SAM reader
    private var samReader: SeReader? = null
    private var samResource: SamResource? = null

    private lateinit var binding: ActivityMainBinding

    private val pluginObserver = ObservablePlugin.PluginObserver { pluginEvent ->
        when (pluginEvent.eventType) {

            PluginEvent.EventType.READER_CONNECTED -> {
                appendColoredText(binding.tvLogs,
                        "Readers have been connected",
                        Color.BLUE)
                appendColoredText(binding.tvLogs, "\n", Color.BLACK)
                try {
                    initReaders()
                } catch (ise: IllegalStateException) {
                                            appendColoredText(binding.tvLogs,
                                                    ise.message!!,
                                                    Color.RED)
                }

                appendColoredText(binding.tvLogs,
                        LINE_SEPARATOR,
                        Color.BLACK)

                (seReader as? ObservableReader)?.startSeDetection(ObservableReader.PollingMode.REPEATING)

                enableSwPower(true)
            }
            PluginEvent.EventType.READER_DISCONNECTED -> {
                appendColoredText(binding.tvLogs,
                        "Readers have been disconnected",
                        Color.BLUE)
                appendColoredText(binding.tvLogs, "\n", Color.BLACK)
                appendColoredText(binding.tvLogs,
                        LINE_SEPARATOR,
                        Color.BLACK)
                enableSwPower(true)
            }
            null -> {

            }
        }
    }

    private val readerObserver = ObservableReader.ReaderObserver { readerEvent ->
        runOnUiThread {
            when (readerEvent.eventType) {
                ReaderEvent.EventType.TIMEOUT_ERROR -> TODO()
                ReaderEvent.EventType.SE_INSERTED -> {
                    appendColoredText(binding.tvLogs,
                            R.string.smart_card_detected,
                            Color.GREEN)
                    appendColoredText(binding.tvLogs,
                            LINE_SEPARATOR,
                            Color.BLACK)

                    runCalypsoTransaction()
                }
                ReaderEvent.EventType.SE_MATCHED -> {
                    appendColoredText(binding.tvLogs,
                            R.string.se_matched,
                            Color.GREEN)
                    appendColoredText(binding.tvLogs,
                            LINE_SEPARATOR,
                            Color.BLACK)

                    runCalypsoTransaction()
                }
                ReaderEvent.EventType.SE_REMOVED -> {
                    appendColoredText(binding.tvLogs,
                            R.string.smart_card_removed,
                            Color.RED)
                    appendColoredText(binding.tvLogs,
                            LINE_SEPARATOR,
                            Color.BLACK)
                }
                null -> {
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

        val selectionsResult = try {
            seSelection.processExplicitSelection(seReader)
        } catch (e: Exception) {
            null
        }

        if (selectionsResult?.hasActiveSelection() == true) {
            Timber.d("selectionsResult.hasActiveSelection")
            val matchingSelection = selectionsResult.activeSelection

            binding.tvLogs.append("The selection of the PO has succeeded.\n")
            /* Go on with the reading of the first record of the EventLog file */
            binding.tvLogs.append(" ---- \n")
            binding.tvLogs.append("2nd PO exchange: \n")
            binding.tvLogs.append("open and close a secure session to perform authentication.\n")
            binding.tvLogs.append(" ---- \n")

            val calypsoPo = matchingSelection.matchingSe as CalypsoPo

            val poTransaction = PoTransaction(
                PoResource(seReader, calypsoPo),
                    samResource, SecuritySettings()
            )

            /*
             * Prepare the reading order and keep the associated parser for later use once the
             * transaction has been processed.
             */
            val readEventLogParserIndex = poTransaction.prepareReadRecordsCmd(
                CalypsoClassicInfo.SFI_EventLog, ReadDataStructure.SINGLE_RECORD_DATA,
                CalypsoClassicInfo.RECORD_NUMBER_1,
                    String.format("EventLog (SFI=%02X, recnbr=%d))",
                        CalypsoClassicInfo.SFI_EventLog,
                        CalypsoClassicInfo.RECORD_NUMBER_1
                    ))

            /*
             * Open Session for the debit key
             */
            var poProcessStatus = poTransaction.processOpening(
                    PoTransaction.ModificationMode.ATOMIC,
                    PoTransaction.SessionAccessLevel.SESSION_LVL_DEBIT, 0.toByte(), 0.toByte())

            check(poProcessStatus) { "processingOpening failure.\n" }

            if (!poTransaction.wasRatified()) {
                appendColoredText(binding.tvLogs,
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
                        CalypsoClassicInfo.RECORD_NUMBER_1
                    ))

            poProcessStatus = poTransaction.processPoCommandsInSession()

            /*
                             * Retrieve the data read from the parser updated during the transaction process
                             */
            val eventLog = (poTransaction.getResponseParser(readEventLogParserIndexBis) as ReadRecordsRespPars).records[CalypsoClassicInfo.RECORD_NUMBER_1.toInt()]

            /* Log the result */
            binding.tvLogs.append("EventLog file data: " + ByteArrayUtil.toHex(eventLog) + "\n")

            check(poProcessStatus) { "processPoCommandsInSession failure.\n" }

            /*
             * Closes the Secure Session.
             */
            binding.tvLogs.append("PO Calypso session: Closing\n")


            /*
             * A ratification command will be sent (CONTACTLESS_MODE).
             */
            poProcessStatus = poTransaction.processClosing(ChannelControl.CLOSE_AFTER)

            check(poProcessStatus) { "processClosing failure.\n" }

            binding.tvLogs.append(" ---- \n")
            binding.tvLogs.append("End of the Calypso PO processing.\n")
            binding.tvLogs.append(" ---- \n")
        } else run {
            appendColoredText(binding.tvLogs,
                    "The selection of the PO has failed.",
                    Color.RED)
        }

        // Reader is now in card detection mode
        Timber.d("notifySeProcessed")
        (seReader as? ObservableReader)?.notifySeProcessed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Manages power switch
        binding.swPower.setOnCheckedChangeListener { _, isChecked ->
            enableSwPower(false)
            powerReaders(isChecked)
        }

        // Starts Keyple
        try {
            createPlugin(Cone2Factory())
        } catch (e: KeyplePluginInstantiationException) {
            appendColoredText(binding.tvLogs, e.message!!, Color.RED)
        }
    }

    override fun onStart() {
        super.onStart()

        plugin.addObserver(pluginObserver)
    }

    override fun onStop() {
        super.onStop()
        plugin.removeObserver(pluginObserver)
        (seReader as? ObservableReader)?.removeObserver(readerObserver)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_clear_all) {
            binding.tvLogs.text = ""
        }
        return super.onOptionsItemSelected(item)
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
            (seReader as? ObservableReader)?.addObserver(this@HomeActivity.readerObserver)
        } catch (e: Exception) {
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
        appendColoredText(binding.tvLogs, LINE_SEPARATOR, Color.BLACK)
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

    private fun enableSwPower (on: Boolean) {
        runOnUiThread {
            binding.swPower.isEnabled = on
        }
    }
}
