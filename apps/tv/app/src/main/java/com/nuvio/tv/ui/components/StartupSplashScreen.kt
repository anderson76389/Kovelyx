package com.nuvio.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun StartupSplashScreen(
    profileColorHex: String?,
    profileBackgroundUrl: String?,
    backgroundCacheKey: String? = null,
    skipGradient: Boolean = false,
    brandWordmarkRes: Int? = null,
    modifier: Modifier = Modifier
) {
    val avatarColor = remember(profileColorHex) {
        profileColorHex?.let {
            runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
        } ?: Color(0xFF1E88E5)
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (!profileBackgroundUrl.isNullOrBlank()) {
            // Réutiliser le rendu de profil : GIF animés et vidéos locales
            // en boucle silencieuse, avec un fond image en secours.
            CustomProfileBackgroundImage(
                imageUrl = profileBackgroundUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!skipGradient) {
            val baseBg = Color(0xFF121212)
            val baseBgElevated = Color(0xFF1E1E1E)
            val gradientTop = lerp(baseBgElevated, avatarColor, 0.3f)
            val gradientMid = lerp(baseBg, avatarColor, 0.14f)
            val halfFadeStrong = avatarColor.copy(alpha = 0.26f)
            val halfFadeSoft = avatarColor.copy(alpha = 0.08f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to gradientTop,
                                0.42f to gradientMid,
                                1f to baseBg
                            )
                        )
                    )
                    .background(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to halfFadeStrong,
                                0.45f to halfFadeSoft,
                                0.72f to Color.Transparent,
                                1f to Color.Transparent
                            )
                        )
                    )
            )
        }

        // Positionne la marque au centre réel de l'écran, sans inclure
        // l'indicateur de progression dans le calcul du centrage.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            BrandWordmark(
                modifier = Modifier
                    .align(Alignment.Center)
                    .height(48.dp),
                contentDescription = stringResource(R.string.cd_nuvio_logo),
                drawableOverride = brandWordmarkRes
            )
            LoadingIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 88.dp)
                    .size(NuvioTheme.spacing.xxxl)
            )
        }
    }
}
