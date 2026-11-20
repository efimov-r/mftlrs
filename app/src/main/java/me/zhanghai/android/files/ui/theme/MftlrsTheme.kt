/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import me.zhanghai.android.files.R

// Тема Compose с палитрой из colors_mftlrs.xml — те же цвета, что у тем на Views (themes_mftlrs.xml).
// isSystemInDarkTheme() видит ночной режим приложения: NightModeHelper задаёт localNightMode активити.
@Composable
fun MftlrsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme() else lightScheme(),
        content = content,
    )
}

@Composable
private fun lightScheme(): ColorScheme = lightColorScheme(
    primary = colorResource(R.color.mftlrs_light_primary),
    onPrimary = colorResource(R.color.mftlrs_light_on_primary),
    primaryContainer = colorResource(R.color.mftlrs_light_primary_container),
    onPrimaryContainer = colorResource(R.color.mftlrs_light_on_primary_container),
    inversePrimary = colorResource(R.color.mftlrs_light_primary_inverse),
    secondary = colorResource(R.color.mftlrs_light_secondary),
    onSecondary = colorResource(R.color.mftlrs_light_on_secondary),
    secondaryContainer = colorResource(R.color.mftlrs_light_secondary_container),
    onSecondaryContainer = colorResource(R.color.mftlrs_light_on_secondary_container),
    tertiary = colorResource(R.color.mftlrs_light_tertiary),
    onTertiary = colorResource(R.color.mftlrs_light_on_tertiary),
    tertiaryContainer = colorResource(R.color.mftlrs_light_tertiary_container),
    onTertiaryContainer = colorResource(R.color.mftlrs_light_on_tertiary_container),
    background = colorResource(R.color.mftlrs_light_background),
    onBackground = colorResource(R.color.mftlrs_light_on_background),
    surface = colorResource(R.color.mftlrs_light_surface),
    onSurface = colorResource(R.color.mftlrs_light_on_surface),
    surfaceVariant = colorResource(R.color.mftlrs_light_surface_variant),
    onSurfaceVariant = colorResource(R.color.mftlrs_light_on_surface_variant),
    surfaceTint = colorResource(R.color.mftlrs_light_primary),
    inverseSurface = colorResource(R.color.mftlrs_light_surface_inverse),
    inverseOnSurface = colorResource(R.color.mftlrs_light_on_surface_inverse),
    error = colorResource(R.color.mftlrs_light_error),
    onError = colorResource(R.color.mftlrs_light_on_error),
    errorContainer = colorResource(R.color.mftlrs_light_error_container),
    onErrorContainer = colorResource(R.color.mftlrs_light_on_error_container),
    outline = colorResource(R.color.mftlrs_light_outline),
    outlineVariant = colorResource(R.color.mftlrs_light_outline_variant),
    surfaceBright = colorResource(R.color.mftlrs_light_surface_bright),
    surfaceDim = colorResource(R.color.mftlrs_light_surface_dim),
    surfaceContainerLowest = colorResource(R.color.mftlrs_light_surface_container_lowest),
    surfaceContainerLow = colorResource(R.color.mftlrs_light_surface_container_low),
    surfaceContainer = colorResource(R.color.mftlrs_light_surface_container),
    surfaceContainerHigh = colorResource(R.color.mftlrs_light_surface_container_high),
    surfaceContainerHighest = colorResource(R.color.mftlrs_light_surface_container_highest),
)

@Composable
private fun darkScheme(): ColorScheme = darkColorScheme(
    primary = colorResource(R.color.mftlrs_dark_primary),
    onPrimary = colorResource(R.color.mftlrs_dark_on_primary),
    primaryContainer = colorResource(R.color.mftlrs_dark_primary_container),
    onPrimaryContainer = colorResource(R.color.mftlrs_dark_on_primary_container),
    inversePrimary = colorResource(R.color.mftlrs_dark_primary_inverse),
    secondary = colorResource(R.color.mftlrs_dark_secondary),
    onSecondary = colorResource(R.color.mftlrs_dark_on_secondary),
    secondaryContainer = colorResource(R.color.mftlrs_dark_secondary_container),
    onSecondaryContainer = colorResource(R.color.mftlrs_dark_on_secondary_container),
    tertiary = colorResource(R.color.mftlrs_dark_tertiary),
    onTertiary = colorResource(R.color.mftlrs_dark_on_tertiary),
    tertiaryContainer = colorResource(R.color.mftlrs_dark_tertiary_container),
    onTertiaryContainer = colorResource(R.color.mftlrs_dark_on_tertiary_container),
    background = colorResource(R.color.mftlrs_dark_background),
    onBackground = colorResource(R.color.mftlrs_dark_on_background),
    surface = colorResource(R.color.mftlrs_dark_surface),
    onSurface = colorResource(R.color.mftlrs_dark_on_surface),
    surfaceVariant = colorResource(R.color.mftlrs_dark_surface_variant),
    onSurfaceVariant = colorResource(R.color.mftlrs_dark_on_surface_variant),
    surfaceTint = colorResource(R.color.mftlrs_dark_primary),
    inverseSurface = colorResource(R.color.mftlrs_dark_surface_inverse),
    inverseOnSurface = colorResource(R.color.mftlrs_dark_on_surface_inverse),
    error = colorResource(R.color.mftlrs_dark_error),
    onError = colorResource(R.color.mftlrs_dark_on_error),
    errorContainer = colorResource(R.color.mftlrs_dark_error_container),
    onErrorContainer = colorResource(R.color.mftlrs_dark_on_error_container),
    outline = colorResource(R.color.mftlrs_dark_outline),
    outlineVariant = colorResource(R.color.mftlrs_dark_outline_variant),
    surfaceBright = colorResource(R.color.mftlrs_dark_surface_bright),
    surfaceDim = colorResource(R.color.mftlrs_dark_surface_dim),
    surfaceContainerLowest = colorResource(R.color.mftlrs_dark_surface_container_lowest),
    surfaceContainerLow = colorResource(R.color.mftlrs_dark_surface_container_low),
    surfaceContainer = colorResource(R.color.mftlrs_dark_surface_container),
    surfaceContainerHigh = colorResource(R.color.mftlrs_dark_surface_container_high),
    surfaceContainerHighest = colorResource(R.color.mftlrs_dark_surface_container_highest),
)
