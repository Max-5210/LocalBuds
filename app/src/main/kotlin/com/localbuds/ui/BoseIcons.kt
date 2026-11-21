package com.localbuds.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom vector icons styled after the official Bose application and sleek AMOLED interface.
 */
object BoseIcons {

    val EarbudLeft: ImageVector by lazy {
        ImageVector.Builder(
            name = "EarbudLeft",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 96f,
            viewportHeight = 96f
        ).apply {
            // Top silicone stability band umbrella fin (smooth cubic Bezier contour)
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 0.72f,
                stroke = null,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(35f, 19f)
                curveTo(32.5f, 12.5f, 35.5f, 7f, 42.5f, 6f)
                curveTo(51.5f, 4.8f, 64.5f, 9.2f, 72f, 17.5f)
                curveTo(77.5f, 23.6f, 80f, 32f, 78.5f, 40f)
                curveTo(77f, 34.5f, 73.2f, 28.5f, 67.5f, 23.8f)
                curveTo(60f, 17.6f, 50f, 14.5f, 41f, 16.2f)
                curveTo(38.2f, 16.8f, 36.2f, 17.8f, 35f, 19f)
                close()
            }
            // Inner ear-tip nozzle & acoustic bowl wing
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 0.85f,
                stroke = null,
                pathFillType = PathFillType.NonZero
            ) {
                // Protruding left ear-tip cushion
                moveTo(30.5f, 23.5f)
                curveTo(20.5f, 26.5f, 14f, 34.5f, 14f, 44.5f)
                curveTo(14f, 54.5f, 20f, 62.5f, 29.5f, 65.5f)
                curveTo(32f, 66.3f, 34f, 64.8f, 34f, 62f)
                lineTo(34f, 26.5f)
                curveTo(34f, 23.8f, 32.5f, 22.8f, 30.5f, 23.5f)
                close()
                // Right stability bowl wing
                moveTo(52f, 17.5f)
                curveTo(66.5f, 18.5f, 78.5f, 27.5f, 80.5f, 40.5f)
                curveTo(82.5f, 53.5f, 74.5f, 66.5f, 61.5f, 71.5f)
                curveTo(57.5f, 73f, 54f, 72.5f, 53.5f, 69.5f)
                lineTo(51.5f, 20f)
                curveTo(51.5f, 18.2f, 51.6f, 17.5f, 52f, 17.5f)
                close()
            }
            // Main sculpted metallic touch stem blade with top oval mic port cutout (EvenOdd)
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 1f,
                stroke = null,
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(41.5f, 17.5f)
                curveTo(49.5f, 17.5f, 55.8f, 23.5f, 56.5f, 31.5f)
                lineTo(58f, 78.5f)
                curveTo(58.2f, 86.2f, 51.8f, 92f, 42.5f, 92f)
                curveTo(33.2f, 92f, 26.8f, 86.2f, 27f, 78.5f)
                lineTo(28.5f, 31.5f)
                curveTo(29.2f, 23.5f, 34.5f, 17.5f, 41.5f, 17.5f)
                close()
                // Top oval microphone mesh cutout
                moveTo(41.5f, 23.5f)
                curveTo(44.5f, 23.5f, 47f, 26.4f, 47f, 30f)
                curveTo(47f, 33.6f, 44.5f, 36.5f, 41.5f, 36.5f)
                curveTo(38.5f, 36.5f, 36f, 33.6f, 36f, 30f)
                curveTo(36f, 26.4f, 38.5f, 23.5f, 41.5f, 23.5f)
                close()
            }
        }.build()
    }

    val EarbudRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "EarbudRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 96f,
            viewportHeight = 96f
        ).apply {
            // Top silicone stability band umbrella fin (mirrored cubic Bezier contour)
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 0.72f,
                stroke = null,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(61f, 19f)
                curveTo(63.5f, 12.5f, 60.5f, 7f, 53.5f, 6f)
                curveTo(44.5f, 4.8f, 31.5f, 9.2f, 24f, 17.5f)
                curveTo(18.5f, 23.6f, 16f, 32f, 17.5f, 40f)
                curveTo(19f, 34.5f, 22.8f, 28.5f, 28.5f, 23.8f)
                curveTo(36f, 17.6f, 46f, 14.5f, 55f, 16.2f)
                curveTo(57.8f, 16.8f, 59.8f, 17.8f, 61f, 19f)
                close()
            }
            // Inner ear-tip nozzle & acoustic bowl wing (mirrored)
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 0.85f,
                stroke = null,
                pathFillType = PathFillType.NonZero
            ) {
                // Protruding right ear-tip cushion
                moveTo(65.5f, 23.5f)
                curveTo(75.5f, 26.5f, 82f, 34.5f, 82f, 44.5f)
                curveTo(82f, 54.5f, 76f, 62.5f, 66.5f, 65.5f)
                curveTo(64f, 66.3f, 62f, 64.8f, 62f, 62f)
                lineTo(62f, 26.5f)
                curveTo(62f, 23.8f, 63.5f, 22.8f, 65.5f, 23.5f)
                close()
                // Left stability bowl wing
                moveTo(44f, 17.5f)
                curveTo(29.5f, 18.5f, 17.5f, 27.5f, 15.5f, 40.5f)
                curveTo(13.5f, 53.5f, 21.5f, 66.5f, 34.5f, 71.5f)
                curveTo(38.5f, 73f, 42f, 72.5f, 42.5f, 69.5f)
                lineTo(44.5f, 20f)
                curveTo(44.5f, 18.2f, 44.4f, 17.5f, 44f, 17.5f)
                close()
            }
            // Main sculpted metallic touch stem blade with top oval mic port cutout (EvenOdd)
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 1f,
                stroke = null,
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(54.5f, 17.5f)
                curveTo(46.5f, 17.5f, 40.2f, 23.5f, 39.5f, 31.5f)
                lineTo(38f, 78.5f)
                curveTo(37.8f, 86.2f, 44.2f, 92f, 53.5f, 92f)
                curveTo(62.8f, 92f, 69.2f, 86.2f, 69f, 78.5f)
                lineTo(67.5f, 31.5f)
                curveTo(66.8f, 23.5f, 61.5f, 17.5f, 54.5f, 17.5f)
                close()
                // Top oval microphone mesh cutout
                moveTo(54.5f, 23.5f)
                curveTo(57.5f, 23.5f, 60f, 26.4f, 60f, 30f)
                curveTo(60f, 33.6f, 57.5f, 36.5f, 54.5f, 36.5f)
                curveTo(51.5f, 36.5f, 49f, 33.6f, 49f, 30f)
                curveTo(49f, 26.4f, 51.5f, 23.5f, 54.5f, 23.5f)
                close()
            }
        }.build()
    }

    val EarbudCase: ImageVector by lazy {
        ImageVector.Builder(
            name = "EarbudCase",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 1f,
                stroke = null
            ) {
                // Case lid
                moveTo(5f, 7f)
                curveTo(5f, 4.8f, 6.8f, 3f, 9f, 3f)
                lineTo(15f, 3f)
                curveTo(17.2f, 3f, 19f, 4.8f, 19f, 7f)
                lineTo(19f, 9f)
                lineTo(5f, 9f)
                close()
                // Case body
                moveTo(5f, 11f)
                lineTo(19f, 11f)
                lineTo(19f, 17f)
                curveTo(19f, 19.8f, 16.8f, 22f, 14f, 22f)
                lineTo(10f, 22f)
                curveTo(7.2f, 22f, 5f, 19.8f, 5f, 17f)
                close()
                // LED indicator dot
                moveTo(11.2f, 14f)
                curveTo(11.2f, 13.6f, 11.6f, 13.2f, 12f, 13.2f)
                curveTo(12.4f, 13.2f, 12.8f, 13.6f, 12.8f, 14f)
                curveTo(12.8f, 14.4f, 12.4f, 14.8f, 12f, 14.8f)
                curveTo(11.6f, 14.8f, 11.2f, 14.4f, 11.2f, 14f)
                close()
            }
        }.build()
    }

    val Quiet: ImageVector by lazy {
        ImageVector.Builder(
            name = "Quiet",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Shield / ANC icon
                moveTo(12f, 2f)
                lineTo(4f, 5f)
                verticalLineToRelative(6.5f)
                curveTo(4f, 16.8f, 7.4f, 21.6f, 12f, 22.8f)
                curveTo(16.6f, 21.6f, 20f, 16.8f, 20f, 11.5f)
                verticalLineTo(5f)
                lineTo(12f, 2f)
                close()
                // Crescent inside shield
                moveTo(11.5f, 8f)
                curveTo(13.8f, 8.4f, 15.5f, 10.4f, 15.5f, 12.8f)
                curveTo(15.5f, 15.2f, 13.8f, 17.2f, 11.5f, 17.6f)
                curveTo(12f, 17.1f, 12.4f, 16.2f, 12.4f, 12.8f)
                curveTo(12.4f, 9.4f, 12f, 8.5f, 11.5f, 8f)
                close()
            }
        }.build()
    }

    val Aware: ImageVector by lazy {
        ImageVector.Builder(
            name = "Aware",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Ear outline (Aware / transparency mode)
                moveTo(11f, 3.5f)
                curveTo(7.4f, 3.5f, 4.5f, 6.4f, 4.5f, 10f)
                curveTo(4.5f, 13f, 6.5f, 15.8f, 8.5f, 17.8f)
                curveTo(9.5f, 18.8f, 10.5f, 20.5f, 11f, 21.5f)
                moveTo(11f, 3.5f)
                curveTo(14.6f, 3.5f, 17.5f, 6.4f, 17.5f, 10f)
                curveTo(17.5f, 11.8f, 16.7f, 13.5f, 15.5f, 14.5f)
                curveTo(14.2f, 15.5f, 13f, 16.2f, 12.5f, 17.5f)
                // Sound waves entering
                moveTo(18f, 5.5f)
                curveTo(20f, 7.5f, 21f, 9.8f, 21f, 12f)
                moveTo(20.5f, 3.5f)
                curveTo(23f, 6.2f, 24f, 9.2f, 24f, 12.5f)
            }
        }.build()
    }

    val Immersion: ImageVector by lazy {
        ImageVector.Builder(
            name = "Immersion",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Central sound sphere (Immersion / spatial audio)
                moveTo(12f, 9f)
                curveTo(10.3f, 9f, 9f, 10.3f, 9f, 12f)
                curveTo(9f, 13.7f, 10.3f, 15f, 12f, 15f)
                curveTo(13.7f, 15f, 15f, 13.7f, 15f, 12f)
                curveTo(15f, 10.3f, 13.7f, 9f, 12f, 9f)
                close()
                // 360 degree spatial rings
                moveTo(12f, 4f)
                curveTo(17.5f, 4f, 22f, 7.6f, 22f, 12f)
                curveTo(22f, 16.4f, 17.5f, 20f, 12f, 20f)
                curveTo(6.5f, 20f, 2f, 16.4f, 2f, 12f)
                curveTo(2f, 7.6f, 6.5f, 4f, 12f, 4f)
                close()
                moveTo(4.5f, 8f)
                curveTo(7.2f, 5.5f, 16.8f, 5.5f, 19.5f, 8f)
            }
        }.build()
    }

    val Equalizer: ImageVector by lazy {
        ImageVector.Builder(
            name = "Equalizer",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // EQ slider 1 (bass)
                moveTo(6f, 3f)
                verticalLineTo(21f)
                moveTo(3.5f, 8f)
                lineTo(8.5f, 8f)
                // EQ slider 2 (mid)
                moveTo(12f, 3f)
                verticalLineTo(21f)
                moveTo(9.5f, 15f)
                lineTo(14.5f, 15f)
                // EQ slider 3 (treble)
                moveTo(18f, 3f)
                verticalLineTo(21f)
                moveTo(15.5f, 11f)
                lineTo(20.5f, 11f)
            }
        }.build()
    }

    val Multipoint: ImageVector by lazy {
        ImageVector.Builder(
            name = "Multipoint",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Device 1 (phone)
                moveTo(5f, 3f)
                horizontalLineTo(11f)
                curveTo(11.6f, 3f, 12f, 3.4f, 12f, 4f)
                verticalLineTo(12f)
                curveTo(12f, 12.6f, 11.6f, 13f, 11f, 13f)
                horizontalLineTo(5f)
                curveTo(4.4f, 13f, 4f, 12.6f, 4f, 12f)
                verticalLineTo(4f)
                curveTo(4f, 3.4f, 4.4f, 3f, 5f, 3f)
                close()
                // Device 2 (laptop/tablet)
                moveTo(13f, 11f)
                horizontalLineTo(19f)
                curveTo(19.6f, 11f, 20f, 11.4f, 20f, 12f)
                verticalLineTo(20f)
                curveTo(20f, 20.6f, 19.6f, 21f, 19f, 21f)
                horizontalLineTo(13f)
                curveTo(12.4f, 21f, 12f, 20.6f, 12f, 20f)
                verticalLineTo(12f)
                curveTo(12f, 11.4f, 12.4f, 11f, 13f, 11f)
                close()
            }
        }.build()
    }

    val InEarDetection: ImageVector by lazy {
        ImageVector.Builder(
            name = "InEarDetection",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 4f)
                curveTo(7.6f, 4f, 4f, 7.6f, 4f, 12f)
                curveTo(4f, 15.3f, 6.2f, 18.2f, 8.5f, 20.1f)
                curveTo(9.5f, 20.9f, 10.5f, 21.8f, 11f, 22f)
                moveTo(12f, 4f)
                curveTo(15.9f, 4f, 19f, 7.1f, 19f, 11f)
                curveTo(19f, 12.7f, 18.2f, 14.2f, 17f, 15.2f)
                curveTo(15.8f, 16.2f, 14.5f, 17.5f, 14.5f, 19f)
                // Sensor detection bars
                moveTo(9f, 11f)
                horizontalLineTo(11f)
                moveTo(9f, 13.5f)
                horizontalLineTo(12f)
            }
        }.build()
    }

    val TouchShortcut: ImageVector by lazy {
        ImageVector.Builder(
            name = "TouchShortcut",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Tap / touch gesture finger
                moveTo(10f, 11f)
                verticalLineTo(4.5f)
                curveTo(10f, 3.7f, 10.7f, 3f, 11.5f, 3f)
                curveTo(12.3f, 3f, 13f, 3.7f, 13f, 4.5f)
                verticalLineTo(11f)
                // Tap ripple arc
                moveTo(6.5f, 6.5f)
                curveTo(7.8f, 4.8f, 9.8f, 3.8f, 12f, 3.8f)
                curveTo(14.2f, 3.8f, 16.2f, 4.8f, 17.5f, 6.5f)
                // Hand / palm
                moveTo(7f, 11.5f)
                verticalLineTo(14.5f)
                curveTo(7f, 18.1f, 9.9f, 21f, 13.5f, 21f)
                horizontalLineTo(14.5f)
                curveTo(17.5f, 21f, 20f, 18.5f, 20f, 15.5f)
                verticalLineTo(11f)
            }
        }.build()
    }

    val RootShield: ImageVector by lazy {
        ImageVector.Builder(
            name = "RootShield",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Shield outline
                moveTo(12f, 2f)
                lineTo(4f, 5.5f)
                verticalLineTo(11.5f)
                curveTo(4f, 16.8f, 7.4f, 21.6f, 12f, 22.8f)
                curveTo(16.6f, 21.6f, 20f, 16.8f, 20f, 11.5f)
                verticalLineTo(5.5f)
                lineTo(12f, 2f)
                close()
                // Lightning bolt / root indicator
                moveTo(13f, 7f)
                lineTo(9.5f, 12.5f)
                horizontalLineTo(12.5f)
                lineTo(11f, 17f)
                lineTo(15.5f, 11f)
                horizontalLineTo(12.5f)
                lineTo(13f, 7f)
                close()
            }
        }.build()
    }
}
