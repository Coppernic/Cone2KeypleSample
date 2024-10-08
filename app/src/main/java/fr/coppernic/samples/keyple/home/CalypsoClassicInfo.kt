package fr.coppernic.samples.keyple.home


/**
 * Helper class to provide specific elements to handle Calypso cards.
 *
 *  * AID application selection (default Calypso AID)
 *  * SAM_C1_ATR_REGEX regular expression matching the expected C1 SAM ATR
 *  * Files infos (SFI, rec number, etc) for
 *
 *  * Environment and Holder
 *  * Event Log
 *  * Contract List
 *  * Contracts
 *
 *
 *
 */
class CalypsoClassicInfo {
    companion object {
        /** Calypso default AID  */
        //val AID = "A0000004040125090101"
        /// ** 1TIC.ICA AID */
        val AID = "315449432E494341";
        /** SAM C1 regular expression: platform, version and serial number values are ignored  */
        val SAM_C1_ATR_REGEX = "3B3F9600805A[0-9a-fA-F]{2}80C1[0-9a-fA-F]{14}829000"

        val ATR_REV1_REGEX = "3B8F8001805A0A0103200311........829000.."

        val RECORD_NUMBER_1: Byte = 1
        val RECORD_NUMBER_2: Byte = 2
        val RECORD_NUMBER_3: Byte = 3
        val RECORD_NUMBER_4: Byte = 4

        val SFI_EnvironmentAndHolder = 0x07.toByte()
        val SFI_EventLog = 0x08.toByte()
        val SFI_ContractList = 0x1E.toByte()
        val SFI_Contracts = 0x09.toByte()

        val eventLog_dataFill = "00112233445566778899AABBCCDDEEFF00112233445566778899AABBCC"
    }
}