package com.micharger.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

object AppIcons {

    val Home: ImageVector by lazy {
        ImageVector.Builder(
            name = "Home", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10f, 20f)
                verticalLineToRelative(-6f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(5f)
                verticalLineToRelative(-8f)
                horizontalLineToRelative(3f)
                lineTo(12f, 3f)
                lineTo(2f, 12f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(8f)
                close()
            }
        }.build()
    }

    val Bolt: ImageVector by lazy {
        ImageVector.Builder(
            name = "Bolt", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(13f, 2f)
                lineTo(4.5f, 13.5f)
                lineTo(10.5f, 13.5f)
                lineTo(9f, 22f)
                lineTo(19.5f, 9.5f)
                lineTo(13.2f, 9.5f)
                lineTo(15f, 2f)
                close()
            }
        }.build()
    }

    val Chart: ImageVector by lazy {
        ImageVector.Builder(
            name = "Chart", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 17f)
                horizontalLineTo(7f)
                verticalLineToRelative(-7f)
                horizontalLineToRelative(2f)
                close()
                moveTo(13f, 17f)
                horizontalLineTo(11f)
                verticalLineTo(7f)
                horizontalLineToRelative(2f)
                close()
                moveTo(17f, 17f)
                horizontalLineTo(15f)
                verticalLineTo(13f)
                horizontalLineToRelative(2f)
                close()
            }
        }.build()
    }

    val Tune: ImageVector by lazy {
        ImageVector.Builder(
            name = "Tune", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 17f); verticalLineToRelative(2f); horizontalLineToRelative(6f)
                verticalLineToRelative(-2f); horizontalLineTo(3f); close()
                moveTo(3f, 5f); verticalLineToRelative(2f); horizontalLineToRelative(10f)
                verticalLineTo(5f); horizontalLineTo(3f); close()
                moveTo(13f, 21f); verticalLineToRelative(-2f); horizontalLineToRelative(8f)
                verticalLineToRelative(-2f); horizontalLineToRelative(-8f); verticalLineToRelative(-2f)
                horizontalLineToRelative(-2f); verticalLineToRelative(6f); horizontalLineToRelative(2f); close()
                moveTo(7f, 9f); verticalLineToRelative(2f); horizontalLineTo(3f); verticalLineToRelative(2f)
                horizontalLineToRelative(4f); verticalLineToRelative(2f); horizontalLineToRelative(2f)
                verticalLineTo(9f); horizontalLineTo(7f); close()
                moveTo(21f, 13f); verticalLineToRelative(-2f); horizontalLineTo(11f); verticalLineToRelative(2f)
                horizontalLineToRelative(10f); close()
                moveTo(15f, 9f); horizontalLineToRelative(2f); verticalLineTo(7f); horizontalLineToRelative(4f)
                verticalLineTo(5f); horizontalLineToRelative(-4f); verticalLineTo(3f); horizontalLineToRelative(-2f)
                verticalLineToRelative(6f); close()
            }
        }.build()
    }
}
