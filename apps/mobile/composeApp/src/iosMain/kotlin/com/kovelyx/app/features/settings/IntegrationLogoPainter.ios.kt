package com.kovelyx.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.kovelyx.app.features.simkl.SimklBrandAsset
import com.kovelyx.app.features.simkl.simklBrandPainter
import kovelyx.composeapp.generated.resources.Res
import kovelyx.composeapp.generated.resources.introdb_favicon
import kovelyx.composeapp.generated.resources.mdblist_logo
import kovelyx.composeapp.generated.resources.rating_tmdb
import kovelyx.composeapp.generated.resources.trakt_tv_favicon
import org.jetbrains.compose.resources.painterResource

@Composable
internal actual fun integrationLogoPainter(logo: IntegrationLogo): Painter =
    when (logo) {
        IntegrationLogo.Tmdb -> painterResource(Res.drawable.rating_tmdb)
        IntegrationLogo.Trakt -> painterResource(Res.drawable.trakt_tv_favicon)
        IntegrationLogo.Simkl -> simklBrandPainter(SimklBrandAsset.Glyph)
        IntegrationLogo.MdbList -> painterResource(Res.drawable.mdblist_logo)
        IntegrationLogo.IntroDb -> painterResource(Res.drawable.introdb_favicon)
    }
