package dev.filpper.companion.domain.model

data class FlipperFile(
    val path: String,
    val filename: String,
    val sizeBytes: Int,
    val category: FlipperFileCategory,
)

enum class FlipperFileCategory {
    NFC, RFID, SUB_GHZ, INFRARED, IBUTTON, BAD_USB, OTHER;

    companion object {
        fun fromFileName(name: String): FlipperFileCategory = when (
            name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        ) {
            "nfc" -> NFC
            "rfid" -> RFID
            "sub" -> SUB_GHZ
            "ir" -> INFRARED
            "ibtn" -> IBUTTON
            else -> OTHER
        }

        fun fromPath(path: String): FlipperFileCategory {
            val normalized = path.lowercase()
            return when {
                normalized.startsWith("/ext/badusb/") -> BAD_USB
                normalized.startsWith("/ext/nfc/") -> NFC
                normalized.startsWith("/ext/lfrfid/") -> RFID
                normalized.startsWith("/ext/subghz/") -> SUB_GHZ
                normalized.startsWith("/ext/infrared/") -> INFRARED
                normalized.startsWith("/ext/ibutton/") -> IBUTTON
                else -> fromFileName(path)
            }
        }
    }
}
