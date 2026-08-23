package com.jobradar.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val defaults = Typography()

// A clearer size/weight scale than the Material3 defaults — the default scale reads as
// generic because titles, body text, and labels barely differ in weight or size.
val JobRadarTypography = Typography(
    headlineSmall = defaults.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleMedium = defaults.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = defaults.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = defaults.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = defaults.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 20.sp),
    labelLarge = defaults.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelMedium = defaults.labelMedium.copy(letterSpacing = 0.3.sp),
    labelSmall = defaults.labelSmall.copy(fontSize = 11.5.sp, letterSpacing = 0.3.sp),
)
