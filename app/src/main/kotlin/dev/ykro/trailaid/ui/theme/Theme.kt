package dev.ykro.trailaid.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Trail Aid palette: forest green, safety orange, emergency red, high contrast for sunlight and haste.
val Forest = Color(0xFF2E7D32)
val ForestDark = Color(0xFF1B5E20)
val ForestSoft = Color(0xFFC8E6C9)
val Orange = Color(0xFFF57C00)
val OrangeSoft = Color(0xFFFFE0B2)
val Red = Color(0xFFD32F2F)
val RedSoft = Color(0xFFFFCDD2)
val Canvas = Color(0xFFE8F5E9)
val Ink = Color(0xFF102A14)
val InkMuted = Color(0xFF3F5B44)
val Danger = Red
val DangerSoft = RedSoft
val Success = Forest
val SuccessSoft = Color(0xFFDCEFDD)
// Names used by the shared components copied from Bug Reporter.
val AmberSoft = OrangeSoft
val IndigoSoft = ForestSoft
val Indigo = Forest

private val scheme =
  lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = ForestSoft,
    onPrimaryContainer = ForestDark,
    secondary = Orange,
    onSecondary = Ink,
    secondaryContainer = OrangeSoft,
    onSecondaryContainer = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = ForestSoft,
    onSurfaceVariant = InkMuted,
    outline = Color(0xFFA5D6A7),
    error = Red,
    errorContainer = RedSoft,
    onErrorContainer = Red,
  )

/** Larger body text than default: the screen is read at arm's length, outdoors, in a hurry. */
private val typography =
  Typography().let {
    it.copy(
      bodyLarge = it.bodyLarge.copy(fontSize = 19.sp, lineHeight = 27.sp),
      bodyMedium = it.bodyMedium.copy(fontSize = 16.sp, lineHeight = 23.sp),
      titleLarge = it.titleLarge.copy(fontWeight = FontWeight.Bold),
    )
  }

private val shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))

@Composable
fun TrailAidTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
