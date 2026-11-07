package dev.filpper.companion.presentation.pages.files

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import dev.filpper.companion.R
import dev.filpper.companion.domain.model.FlipperFileCategory

@StringRes
internal fun FlipperFileCategory.titleResource(): Int = when (this) {
    FlipperFileCategory.NFC -> R.string.category_nfc
    FlipperFileCategory.RFID -> R.string.category_rfid
    FlipperFileCategory.SUB_GHZ -> R.string.category_subghz
    FlipperFileCategory.INFRARED -> R.string.category_infrared
    FlipperFileCategory.IBUTTON -> R.string.category_ibutton
    FlipperFileCategory.BAD_USB -> R.string.category_badusb
    FlipperFileCategory.OTHER -> R.string.category_other
}

internal fun FlipperFileCategory.accent(): Color = when (this) {
    FlipperFileCategory.NFC -> Color(0xFF369BFF)
    FlipperFileCategory.RFID -> Color(0xFFFF786D)
    FlipperFileCategory.SUB_GHZ -> Color(0xFFFFA633)
    FlipperFileCategory.INFRARED -> Color(0xFF9A82FF)
    FlipperFileCategory.IBUTTON -> Color(0xFF51C5A7)
    FlipperFileCategory.BAD_USB -> Color(0xFFF29FC3)
    FlipperFileCategory.OTHER -> Color(0xFF8D939C)
}
