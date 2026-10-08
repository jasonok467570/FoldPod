package com.foldpod.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette


data class AlbumTheme(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val wheel: Color,
    val wheelCenter: Color,
    val accent: Color,
)


fun extractAlbumTheme(
    bitmap: Bitmap?
): AlbumTheme {

    if (bitmap == null) {
        return defaultAlbumTheme()
    }


    val palette =
        Palette
            .from(bitmap)

            // 색 후보를 조금 더 많이 확보
            .maximumColorCount(32)

            .generate()


    /*
     * Spotify 느낌을 내려면
     * dominant보다는 vibrant / muted 색을 먼저 사용.
     */
    val baseColor =
        palette.vibrantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: palette.lightVibrantSwatch?.rgb
            ?: palette.darkVibrantSwatch?.rgb
            ?: palette.lightMutedSwatch?.rgb
            ?: palette.darkMutedSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: android.graphics.Color.DKGRAY


    /*
     * ============================
     * Background
     * ============================
     *
     * 이전 값:
     *
     * top    = 0.19
     * bottom = 0.055
     *
     * → 너무 어두웠음.
     *
     * 이제 album hue가 확실히 보이도록 밝힘.
     */
    val backgroundTop =
        transformColor(
            color = baseColor,
            saturationScale = 0.88f,
            targetLightness = 0.31f,
        )

    val backgroundBottom =
        transformColor(
            color = baseColor,
            saturationScale = 0.78f,
            targetLightness = 0.14f,
        )

    /*
     * ============================
     * Wheel
     * ============================
     *
     * Background보다 확실하게 어둡게.
     *
     * 하지만 이전처럼 거의 black까지는
     * 내려가지 않음.
     */
    val wheel =
        transformColor(
            color = baseColor,
            saturationScale = 0.82f,
            targetLightness = 0.095f,
        )


    /*
     * Center button은 wheel보다
     * 한 단계 더 어둡게.
     */
    val wheelCenter =
        transformColor(
            color = baseColor,
            saturationScale = 0.70f,
            targetLightness = 0.045f,
        )


    /*
     * Progress bar / subtle highlight.
     */
    val accent =
        transformColor(
            color = baseColor,
            saturationScale = 0.95f,
            targetLightness = 0.70f,
        )


    return AlbumTheme(
        backgroundTop =
            Color(backgroundTop),

        backgroundBottom =
            Color(backgroundBottom),

        wheel =
            Color(wheel),

        wheelCenter =
            Color(wheelCenter),

        accent =
            Color(accent),
    )
}


private fun transformColor(
    color: Int,
    saturationScale: Float,
    targetLightness: Float,
): Int {

    val hsl =
        FloatArray(3)


    ColorUtils.colorToHSL(
        color,
        hsl
    )


    /*
     * hsl[0] = Hue
     * hsl[1] = Saturation
     * hsl[2] = Lightness
     *
     * Hue는 album art에서 가져온 것을 그대로 유지.
     */


    /*
     * 원본이 약간 탁한 색이어도
     * background에서 색감이 죽지 않도록 함.
     */
    hsl[1] =
        (hsl[1] * saturationScale)
            .coerceIn(
                0.28f,
                0.90f
            )


    hsl[2] =
        targetLightness


    return ColorUtils.HSLToColor(
        hsl
    )
}


private fun defaultAlbumTheme(): AlbumTheme {

    return AlbumTheme(

        backgroundTop =
            Color(0xFF343434),

        backgroundBottom =
            Color(0xFF151515),

        wheel =
            Color(0xFF111111),

        wheelCenter =
            Color(0xFF070707),

        accent =
            Color(0xFFE0E0E0),
    )
}