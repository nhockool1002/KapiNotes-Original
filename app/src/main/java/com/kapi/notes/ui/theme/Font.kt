package com.kapi.notes.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kapi.notes.R

// Brand typefaces from the guidelines: Outfit for headings (modern, rounded-friendly),
// Plus Jakarta Sans for body/UI text (highly readable at small sizes). Bundled as local .ttf
// resources rather than Google Fonts' downloadable-font API so rendering is correct and
// consistent even offline / without Google Play Services on the device.
val OutfitFontFamily = FontFamily(
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_bold, FontWeight.Bold),
    Font(R.font.outfit_extrabold, FontWeight.ExtraBold)
)

val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold)
)
