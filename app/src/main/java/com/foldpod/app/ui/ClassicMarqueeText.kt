package com.foldpod.app.ui

import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** Scroll only overflowing music text, restarting the pause when its identity changes. */
@Composable
internal fun ClassicMarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = fontSize * 1.25f,
    textAlign: TextAlign? = null,
    enabled: Boolean = true,
) {
    val velocity = if (LocalLayoutDirection.current == LayoutDirection.Rtl) (-25).dp else 25.dp
    key(text) {
        Text(
            text = text,
            modifier = modifier.clipToBounds().then(if (enabled) Modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                initialDelayMillis = 1_500,
                repeatDelayMillis = 1_500,
                velocity = velocity,
            ) else Modifier),
            color = color,
            fontSize = fontSize,
            fontFamily = ClassicFontFamily,
            fontWeight = fontWeight,
            lineHeight = lineHeight,
            textAlign = textAlign,
            maxLines = 1,
            softWrap = false,
            overflow = if (enabled) TextOverflow.Clip else TextOverflow.Ellipsis,
            style = LocalTextStyle.current.copy(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}
