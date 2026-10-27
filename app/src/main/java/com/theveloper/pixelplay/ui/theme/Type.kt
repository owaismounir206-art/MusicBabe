package com.theveloper.pixelplay.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R


private val montserrat = GoogleFont("Montserrat")
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs
)

val MontserratFamily = FontFamily(
    Font(googleFont = montserrat, fontProvider = provider, weight = FontWeight.Black),
    Font(googleFont = montserrat, fontProvider = provider, weight = FontWeight.ExtraBold),
    Font(googleFont = montserrat, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = montserrat, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = montserrat, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = montserrat, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = montserrat, fontProvider = provider, weight = FontWeight.Light),
)

val ExpTitleTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = MontserratFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 60.sp,
        textGeometricTransform = TextGeometricTransform(scaleX = 1.5f),
        letterSpacing = (-0.02).em,
        lineHeight = 0.95.em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    displayMedium = TextStyle(
        fontFamily = MontserratFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 50.sp,
        //textGeometricTransform = TextGeometricTransform(scaleX = 1f),
        letterSpacing = (-0.02).em,
        lineHeight = 0.95.em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    titleMedium = TextStyle(
        fontFamily = MontserratFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        textGeometricTransform = TextGeometricTransform(scaleX = 1.3f),
        letterSpacing = (-0.02).em,
        lineHeight = 0.95.em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
)

// Google Sans Flex variable font, the Fluido typeface. Body text keeps the plain shapes (ROND 0);
// titles and numbers use the rounded cut (ROND 100) for a friendlier tone.
@OptIn(ExperimentalTextApi::class)
private fun gflex(weight: FontWeight, rond: Float) = androidx.compose.ui.text.font.Font(
    resId = R.font.gflex_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("ROND", rond)
    )
)

private val FluidoWeights = listOf(
    FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold,
    FontWeight.ExtraBold, FontWeight.Black
)

/** Google Sans Flex, plain: body, labels. The name is historical, kept for every call site. */
val GoogleSansRounded = FontFamily(FluidoWeights.map { gflex(it, 0f) })

/** Google Sans Flex, rounded: display, headline and large titles. */
val FluidoRoundFamily = FontFamily(FluidoWeights.map { gflex(it, 100f) })

/** The Fluido type scale (Material 3) on Google Sans Flex. */
val Typography = Typography(
    displayLarge = TextStyle(fontFamily = FluidoRoundFamily, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = FluidoRoundFamily, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontFamily = FluidoRoundFamily, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = FluidoRoundFamily, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = FluidoRoundFamily, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontFamily = FluidoRoundFamily, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = FluidoRoundFamily, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp)
)

/** Material 3 Expressive "emphasized" styles: one hero moment per screen. */
object FluidoEmphasized {
    val DisplayLarge = TextStyle(fontFamily = FluidoRoundFamily, fontWeight = FontWeight.SemiBold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.5).sp)
    val HeadlineLarge = TextStyle(fontFamily = FluidoRoundFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.2).sp)
    val TitleMedium = TextStyle(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp)
    val LabelLarge = TextStyle(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp)
}
