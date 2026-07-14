package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = PrimaryTealLight,
    secondary = SecondarySlate,
    tertiary = AccentElectricBlue,
    background = Color(0xFF0F172A), // Dark slate
    surface = Color(0xFF1E293B),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryTeal,
    secondary = SecondarySlate,
    tertiary = AccentElectricBlue,
    background = BackgroundOffWhite,
    surface = SurfacePureWhite,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
  )

fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex.replace("0x", "#")))
    } catch (e: Exception) {
        Color(0xFF0F766E) // Fallback to premium teal
    }
}

fun getCustomLightColorScheme(primaryColor: Color): ColorScheme {
    return lightColorScheme(
        primary = primaryColor,
        secondary = Color(0xFF475569),
        tertiary = Color(0xFF2563EB),
        background = Color(0xFFF8FAFC),
        surface = Color(0xFFFFFFFF),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onTertiary = Color.White,
        onBackground = Color(0xFF0F172A),
        onSurface = Color(0xFF0F172A)
    )
}

fun getCustomDarkColorScheme(primaryColor: Color): ColorScheme {
    return darkColorScheme(
        primary = primaryColor,
        secondary = Color(0xFF475569),
        tertiary = Color(0xFF2563EB),
        background = Color(0xFF0F172A),
        surface = Color(0xFF1E293B),
        onPrimary = Color.Black,
        onSecondary = Color.White,
        onBackground = Color(0xFFF8FAFC),
        onSurface = Color(0xFFF8FAFC)
    )
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  customPrimaryHex: String? = null,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      customPrimaryHex != null -> {
        val primaryColor = parseHexColor(customPrimaryHex)
        if (darkTheme) getCustomDarkColorScheme(primaryColor) else getCustomLightColorScheme(primaryColor)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
