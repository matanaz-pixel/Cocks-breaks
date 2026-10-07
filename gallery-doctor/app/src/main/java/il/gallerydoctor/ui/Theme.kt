package il.gallerydoctor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF0B5FA5), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF), onPrimaryContainer = Color(0xFF001C38),
    secondary = Color(0xFF545F71), surface = Color(0xFFFFFBFF), onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE1E2EC), onSurfaceVariant = Color(0xFF44474F),
    error = Color(0xFFBA1A1A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA2C9FF), onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF00497F), onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFBCC7DB), surface = Color(0xFF121316), onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF44474F), onSurfaceVariant = Color(0xFFC5C6D0),
    error = Color(0xFFFFB4AB),
)

/** Large, readable text for a non-technical user: nothing below 16sp. */
private val BigTypography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 27.sp),
    bodySmall = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
)

object Signal {
    val Red = Color(0xFFD32F2F)
    val Yellow = Color(0xFFF9A825)
    val Green = Color(0xFF2E7D32)
}

/** Hebrew UI: the layout direction is forced to RTL regardless of the phone's language. */
@Composable
fun GalleryDoctorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = BigTypography,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
