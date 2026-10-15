package com.roni.blocks.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/*
 * The icons Blocks draws (3.5.0), so the material-icons-core / -extended libraries can go.
 *
 * Generated from Google's Material Icons, "outlined" 24 dp set (Apache License 2.0,
 * https://github.com/google/material-design-icons), the same source the Compose icon libraries were
 * generated from. Each SVG was parsed into PathBuilder calls (no path parsing at runtime) and checked
 * by rendering it against the original SVG. Names match the old `Icons.Outlined.X` /
 * `Icons.AutoMirrored.Outlined.X`; the auto-mirrored ones flip in right-to-left layouts as before.
 *
 * To add an icon: take its SVG from the outlined set and convert it the same way (the planning agent's
 * converter), or ask for it in a task. Don't hand-edit the path data.
 */
object AppIcons {

    /** alarm.svg */
    val Alarm: ImageVector by lazy {
        icon("Alarm") {
            filled {
                moveTo(12.5f, 8f)
                horizontalLineTo(11f)
                verticalLineToRelative(6f)
                lineToRelative(4.75f, 2.85f)
                lineToRelative(0.75f, -1.23f)
                lineToRelative(-4f, -2.37f)
                close()
                moveToRelative(4.837f, -6.19f)
                lineToRelative(4.607f, 3.845f)
                lineToRelative(-1.28f, 1.535f)
                lineToRelative(-4.61f, -3.843f)
                close()
                moveToRelative(-10.674f, 0f)
                lineToRelative(1.282f, 1.536f)
                lineTo(3.337f, 7.19f)
                lineToRelative(-1.28f, -1.536f)
                close()
                moveTo(12f, 4f)
                arcToRelative(9f, 9f, 0f, true, false, 0.001f, 18.001f)
                arcTo(9f, 9f, 0f, false, false, 12f, 4f)
                close()
                moveToRelative(0f, 16f)
                curveToRelative(-3.86f, 0f, -7f, -3.14f, -7f, -7f)
                reflectiveCurveToRelative(3.14f, -7f, 7f, -7f)
                reflectiveCurveToRelative(7f, 3.14f, 7f, 7f)
                reflectiveCurveToRelative(-3.14f, 7f, -7f, 7f)
                close()
            }
        }
    }

    /** arrow_back.svg */
    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack", autoMirror = true) {
            filled {
                moveTo(20f, 11f)
                horizontalLineTo(7.83f)
                lineToRelative(5.59f, -5.59f)
                lineTo(12f, 4f)
                lineToRelative(-8f, 8f)
                lineToRelative(8f, 8f)
                lineToRelative(1.41f, -1.41f)
                lineTo(7.83f, 13f)
                horizontalLineTo(20f)
                verticalLineToRelative(-2f)
                close()
            }
        }
    }

    /** arrow_downward.svg */
    val ArrowDownward: ImageVector by lazy {
        icon("ArrowDownward") {
            filled {
                moveToRelative(20f, 12f)
                lineToRelative(-1.41f, -1.41f)
                lineTo(13f, 16.17f)
                verticalLineTo(4f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(12.17f)
                lineToRelative(-5.58f, -5.59f)
                lineTo(4f, 12f)
                lineToRelative(8f, 8f)
                lineToRelative(8f, -8f)
                close()
            }
        }
    }

    /** arrow_upward.svg */
    val ArrowUpward: ImageVector by lazy {
        icon("ArrowUpward") {
            filled {
                moveToRelative(4f, 12f)
                lineToRelative(1.41f, 1.41f)
                lineTo(11f, 7.83f)
                verticalLineTo(20f)
                horizontalLineToRelative(2f)
                verticalLineTo(7.83f)
                lineToRelative(5.58f, 5.59f)
                lineTo(20f, 12f)
                lineToRelative(-8f, -8f)
                lineToRelative(-8f, 8f)
                close()
            }
        }
    }

    /** bar_chart.svg */
    val BarChart: ImageVector by lazy {
        icon("BarChart") {
            filled {
                moveTo(4f, 9f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(11f)
                horizontalLineTo(4f)
                close()
                moveToRelative(12f, 4f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(7f)
                horizontalLineToRelative(-4f)
                close()
                moveToRelative(-6f, -9f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(16f)
                horizontalLineToRelative(-4f)
                close()
            }
        }
    }

    /** build.svg */
    val Build: ImageVector by lazy {
        icon("Build") {
            filled {
                moveToRelative(22.61f, 18.99f)
                lineToRelative(-9.08f, -9.08f)
                curveToRelative(0.93f, -2.34f, 0.45f, -5.1f, -1.44f, -7f)
                curveTo(9.79f, 0.61f, 6.21f, 0.4f, 3.66f, 2.26f)
                lineTo(7.5f, 6.11f)
                lineTo(6.08f, 7.52f)
                lineTo(2.25f, 3.69f)
                curveTo(0.39f, 6.23f, 0.6f, 9.82f, 2.9f, 12.11f)
                curveToRelative(1.86f, 1.86f, 4.57f, 2.35f, 6.89f, 1.48f)
                lineToRelative(9.11f, 9.11f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                lineToRelative(2.3f, -2.3f)
                curveToRelative(0.4f, -0.38f, 0.4f, -1.01f, 0f, -1.41f)
                close()
                moveToRelative(-3f, 1.6f)
                lineToRelative(-9.46f, -9.46f)
                curveToRelative(-0.61f, 0.45f, -1.29f, 0.72f, -2f, 0.82f)
                curveToRelative(-1.36f, 0.2f, -2.79f, -0.21f, -3.83f, -1.25f)
                curveTo(3.37f, 9.76f, 2.93f, 8.5f, 3f, 7.26f)
                lineToRelative(3.09f, 3.09f)
                lineToRelative(4.24f, -4.24f)
                lineToRelative(-3.09f, -3.09f)
                curveToRelative(1.24f, -0.07f, 2.49f, 0.37f, 3.44f, 1.31f)
                arcToRelative(4.469f, 4.469f, 0f, false, true, 1.24f, 3.96f)
                arcToRelative(4.35f, 4.35f, 0f, false, true, -0.88f, 1.96f)
                lineToRelative(9.45f, 9.45f)
                lineToRelative(-0.88f, 0.89f)
                close()
            }
        }
    }

    /** calendar_month.svg */
    val CalendarMonth: ImageVector by lazy {
        icon("CalendarMonth") {
            filled {
                moveTo(19f, 4f)
                horizontalLineToRelative(-1f)
                verticalLineTo(2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                horizontalLineTo(8f)
                verticalLineTo(2f)
                horizontalLineTo(6f)
                verticalLineToRelative(2f)
                horizontalLineTo(5f)
                curveToRelative(-1.11f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(3f, 20f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(6f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveToRelative(0f, 16f)
                horizontalLineTo(5f)
                verticalLineTo(10f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(10f)
                close()
                moveToRelative(0f, -12f)
                horizontalLineTo(5f)
                verticalLineTo(6f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(2f)
                close()
                moveTo(9f, 14f)
                horizontalLineTo(7f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
                moveToRelative(4f, 0f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
                moveToRelative(4f, 0f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
                moveToRelative(-8f, 4f)
                horizontalLineTo(7f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
                moveToRelative(4f, 0f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
                moveToRelative(4f, 0f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
            }
        }
    }

    /** check.svg */
    val Check: ImageVector by lazy {
        icon("Check") {
            filled {
                moveTo(9f, 16.17f)
                lineTo(4.83f, 12f)
                lineToRelative(-1.42f, 1.41f)
                lineTo(9f, 19f)
                lineTo(21f, 7f)
                lineToRelative(-1.41f, -1.41f)
                lineTo(9f, 16.17f)
                close()
            }
        }
    }

    /** code.svg */
    val Code: ImageVector by lazy {
        icon("Code") {
            filled {
                moveTo(9.4f, 16.6f)
                lineTo(4.8f, 12f)
                lineToRelative(4.6f, -4.6f)
                lineTo(8f, 6f)
                lineToRelative(-6f, 6f)
                lineToRelative(6f, 6f)
                lineToRelative(1.4f, -1.4f)
                close()
                moveToRelative(5.2f, 0f)
                lineToRelative(4.6f, -4.6f)
                lineToRelative(-4.6f, -4.6f)
                lineTo(16f, 6f)
                lineToRelative(6f, 6f)
                lineToRelative(-6f, 6f)
                lineToRelative(-1.4f, -1.4f)
                close()
            }
        }
    }

    /** delete.svg */
    val Delete: ImageVector by lazy {
        icon("Delete") {
            filled {
                moveTo(16f, 9f)
                verticalLineToRelative(10f)
                horizontalLineTo(8f)
                verticalLineTo(9f)
                horizontalLineToRelative(8f)
                moveToRelative(-1.5f, -6f)
                horizontalLineToRelative(-5f)
                lineToRelative(-1f, 1f)
                horizontalLineTo(5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(14f)
                verticalLineTo(4f)
                horizontalLineToRelative(-3.5f)
                lineToRelative(-1f, -1f)
                close()
                moveTo(18f, 7f)
                horizontalLineTo(6f)
                verticalLineToRelative(12f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(8f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                close()
            }
        }
    }

    /** description.svg */
    val Description: ImageVector by lazy {
        icon("Description") {
            filled {
                moveTo(8f, 16f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(2f)
                horizontalLineTo(8f)
                close()
                moveToRelative(0f, -4f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(2f)
                horizontalLineTo(8f)
                close()
                moveToRelative(6f, -10f)
                horizontalLineTo(6f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(16f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 1.99f, 2f)
                horizontalLineTo(18f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8f)
                lineToRelative(-6f, -6f)
                close()
                moveToRelative(4f, 18f)
                horizontalLineTo(6f)
                verticalLineTo(4f)
                horizontalLineToRelative(7f)
                verticalLineToRelative(5f)
                horizontalLineToRelative(5f)
                verticalLineToRelative(11f)
                close()
            }
        }
    }

    /** directions_walk.svg */
    val DirectionsWalk: ImageVector by lazy {
        icon("DirectionsWalk", autoMirror = true) {
            filled {
                moveTo(13.5f, 5.5f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                close()
                moveTo(9.8f, 8.9f)
                lineTo(7f, 23f)
                horizontalLineToRelative(2.1f)
                lineToRelative(1.8f, -8f)
                lineToRelative(2.1f, 2f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-7.5f)
                lineToRelative(-2.1f, -2f)
                lineToRelative(0.6f, -3f)
                curveTo(14.8f, 12f, 16.8f, 13f, 19f, 13f)
                verticalLineToRelative(-2f)
                curveToRelative(-1.9f, 0f, -3.5f, -1f, -4.3f, -2.4f)
                lineToRelative(-1f, -1.6f)
                arcToRelative(2.145f, 2.145f, 0f, false, false, -2.65f, -0.84f)
                lineTo(6f, 8.3f)
                verticalLineTo(13f)
                horizontalLineToRelative(2f)
                verticalLineTo(9.6f)
                lineToRelative(1.8f, -0.7f)
            }
        }
    }

    /** email.svg */
    val Email: ImageVector by lazy {
        icon("Email") {
            filled {
                moveTo(22f, 6f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(12f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(6f)
                close()
                moveToRelative(-2f, 0f)
                lineToRelative(-8f, 5f)
                lineToRelative(-8f, -5f)
                horizontalLineToRelative(16f)
                close()
                moveToRelative(0f, 12f)
                horizontalLineTo(4f)
                verticalLineTo(8f)
                lineToRelative(8f, 5f)
                lineToRelative(8f, -5f)
                verticalLineToRelative(10f)
                close()
            }
        }
    }

    /** event.svg */
    val Event: ImageVector by lazy {
        icon("Event") {
            filled {
                moveTo(19f, 4f)
                horizontalLineToRelative(-1f)
                verticalLineTo(2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                horizontalLineTo(8f)
                verticalLineTo(2f)
                horizontalLineTo(6f)
                verticalLineToRelative(2f)
                horizontalLineTo(5f)
                curveToRelative(-1.11f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(3f, 20f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(6f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveToRelative(0f, 16f)
                horizontalLineTo(5f)
                verticalLineTo(10f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(10f)
                close()
                moveToRelative(0f, -12f)
                horizontalLineTo(5f)
                verticalLineTo(6f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(2f)
                close()
                moveToRelative(-7f, 5f)
                horizontalLineToRelative(5f)
                verticalLineToRelative(5f)
                horizontalLineToRelative(-5f)
                close()
            }
        }
    }

    /** fitness_center.svg */
    val FitnessCenter: ImageVector by lazy {
        icon("FitnessCenter") {
            filled {
                moveTo(20.57f, 14.86f)
                lineTo(22f, 13.43f)
                lineTo(20.57f, 12f)
                lineTo(17f, 15.57f)
                lineTo(8.43f, 7f)
                lineTo(12f, 3.43f)
                lineTo(10.57f, 2f)
                lineTo(9.14f, 3.43f)
                lineTo(7.71f, 2f)
                lineTo(5.57f, 4.14f)
                lineTo(4.14f, 2.71f)
                lineTo(2.71f, 4.14f)
                lineToRelative(1.43f, 1.43f)
                lineTo(2f, 7.71f)
                lineToRelative(1.43f, 1.43f)
                lineTo(2f, 10.57f)
                lineTo(3.43f, 12f)
                lineTo(7f, 8.43f)
                lineTo(15.57f, 17f)
                lineTo(12f, 20.57f)
                lineTo(13.43f, 22f)
                lineToRelative(1.43f, -1.43f)
                lineTo(16.29f, 22f)
                lineToRelative(2.14f, -2.14f)
                lineToRelative(1.43f, 1.43f)
                lineToRelative(1.43f, -1.43f)
                lineToRelative(-1.43f, -1.43f)
                lineTo(22f, 16.29f)
                lineToRelative(-1.43f, -1.43f)
                close()
            }
        }
    }

    /** help_outline.svg */
    val HelpOutline: ImageVector by lazy {
        icon("HelpOutline") {
            filled {
                moveTo(11f, 18f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                close()
                moveToRelative(1f, -16f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveToRelative(0f, 18f)
                curveToRelative(-4.41f, 0f, -8f, -3.59f, -8f, -8f)
                reflectiveCurveToRelative(3.59f, -8f, 8f, -8f)
                reflectiveCurveToRelative(8f, 3.59f, 8f, 8f)
                reflectiveCurveToRelative(-3.59f, 8f, -8f, 8f)
                close()
                moveToRelative(0f, -14f)
                curveToRelative(-2.21f, 0f, -4f, 1.79f, -4f, 4f)
                horizontalLineToRelative(2f)
                curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
                reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
                curveToRelative(0f, 2f, -3f, 1.75f, -3f, 5f)
                horizontalLineToRelative(2f)
                curveToRelative(0f, -2.25f, 3f, -2.5f, 3f, -5f)
                curveToRelative(0f, -2.21f, -1.79f, -4f, -4f, -4f)
                close()
            }
        }
    }

    /** history.svg */
    val History: ImageVector by lazy {
        icon("History") {
            filled {
                moveTo(13f, 3f)
                arcToRelative(9f, 9f, 0f, false, false, -9f, 9f)
                horizontalLineTo(1f)
                lineToRelative(3.89f, 3.89f)
                lineToRelative(0.07f, 0.14f)
                lineTo(9f, 12f)
                horizontalLineTo(6f)
                curveToRelative(0f, -3.87f, 3.13f, -7f, 7f, -7f)
                reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
                reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f)
                curveToRelative(-1.93f, 0f, -3.68f, -0.79f, -4.94f, -2.06f)
                lineToRelative(-1.42f, 1.42f)
                arcTo(8.954f, 8.954f, 0f, false, false, 13f, 21f)
                arcToRelative(9f, 9f, 0f, false, false, 0f, -18f)
                close()
                moveToRelative(-1f, 5f)
                verticalLineToRelative(5f)
                lineToRelative(4.25f, 2.52f)
                lineToRelative(0.77f, -1.28f)
                lineToRelative(-3.52f, -2.09f)
                verticalLineTo(8f)
                close()
            }
        }
    }

    /** home.svg */
    val Home: ImageVector by lazy {
        icon("Home") {
            filled {
                moveToRelative(12f, 5.69f)
                lineToRelative(5f, 4.5f)
                verticalLineTo(18f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-6f)
                horizontalLineTo(9f)
                verticalLineToRelative(6f)
                horizontalLineTo(7f)
                verticalLineToRelative(-7.81f)
                lineToRelative(5f, -4.5f)
                moveTo(12f, 3f)
                lineTo(2f, 12f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(8f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(-6f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(-8f)
                horizontalLineToRelative(3f)
                lineTo(12f, 3f)
                close()
            }
        }
    }

    /** hourglass_bottom.svg */
    val HourglassBottom: ImageVector by lazy {
        icon("HourglassBottom") {
            filled {
                moveToRelative(18f, 22f)
                lineToRelative(-0.01f, -6f)
                lineTo(14f, 12f)
                lineToRelative(3.99f, -4.01f)
                lineTo(18f, 2f)
                horizontalLineTo(6f)
                verticalLineToRelative(6f)
                lineToRelative(4f, 4f)
                lineToRelative(-4f, 3.99f)
                verticalLineTo(22f)
                horizontalLineToRelative(12f)
                close()
                moveTo(8f, 7.5f)
                verticalLineTo(4f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(3.5f)
                lineToRelative(-4f, 4f)
                lineToRelative(-4f, -4f)
                close()
            }
        }
    }

    /** info.svg */
    val Info: ImageVector by lazy {
        icon("Info") {
            filled {
                moveTo(11f, 7f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-2f)
                close()
                moveToRelative(0f, 4f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(-2f)
                close()
                moveToRelative(1f, -9f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveToRelative(0f, 18f)
                curveToRelative(-4.41f, 0f, -8f, -3.59f, -8f, -8f)
                reflectiveCurveToRelative(3.59f, -8f, 8f, -8f)
                reflectiveCurveToRelative(8f, 3.59f, 8f, 8f)
                reflectiveCurveToRelative(-3.59f, 8f, -8f, 8f)
                close()
            }
        }
    }

    /** insights.svg */
    val Insights: ImageVector by lazy {
        icon("Insights") {
            filled {
                moveTo(21f, 8f)
                curveToRelative(-1.45f, 0f, -2.26f, 1.44f, -1.93f, 2.51f)
                lineToRelative(-3.55f, 3.56f)
                curveToRelative(-0.3f, -0.09f, -0.74f, -0.09f, -1.04f, 0f)
                lineToRelative(-2.55f, -2.55f)
                curveTo(12.27f, 10.45f, 11.46f, 9f, 10f, 9f)
                curveToRelative(-1.45f, 0f, -2.27f, 1.44f, -1.93f, 2.52f)
                lineToRelative(-4.56f, 4.55f)
                curveTo(2.44f, 15.74f, 1f, 16.55f, 1f, 18f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                curveToRelative(1.45f, 0f, 2.26f, -1.44f, 1.93f, -2.51f)
                lineToRelative(4.55f, -4.56f)
                curveToRelative(0.3f, 0.09f, 0.74f, 0.09f, 1.04f, 0f)
                lineToRelative(2.55f, 2.55f)
                curveTo(12.73f, 16.55f, 13.54f, 18f, 15f, 18f)
                curveToRelative(1.45f, 0f, 2.27f, -1.44f, 1.93f, -2.52f)
                lineToRelative(3.56f, -3.55f)
                curveToRelative(1.07f, 0.33f, 2.51f, -0.48f, 2.51f, -1.93f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
            }
            filled {
                moveToRelative(15f, 9f)
                lineToRelative(0.94f, -2.07f)
                lineTo(18f, 6f)
                lineToRelative(-2.06f, -0.93f)
                lineTo(15f, 3f)
                lineToRelative(-0.92f, 2.07f)
                lineTo(12f, 6f)
                lineToRelative(2.08f, 0.93f)
                close()
                moveTo(3.5f, 11f)
                lineTo(4f, 9f)
                lineToRelative(2f, -0.5f)
                lineTo(4f, 8f)
                lineToRelative(-0.5f, -2f)
                lineTo(3f, 8f)
                lineToRelative(-2f, 0.5f)
                lineTo(3f, 9f)
                close()
            }
        }
    }

    /** keyboard_arrow_left.svg */
    val KeyboardArrowLeft: ImageVector by lazy {
        icon("KeyboardArrowLeft", autoMirror = true) {
            filled {
                moveTo(15.41f, 16.59f)
                lineTo(10.83f, 12f)
                lineToRelative(4.58f, -4.59f)
                lineTo(14f, 6f)
                lineToRelative(-6f, 6f)
                lineToRelative(6f, 6f)
                lineToRelative(1.41f, -1.41f)
                close()
            }
        }
    }

    /** keyboard_arrow_right.svg */
    val KeyboardArrowRight: ImageVector by lazy {
        icon("KeyboardArrowRight", autoMirror = true) {
            filled {
                moveTo(8.59f, 16.59f)
                lineTo(13.17f, 12f)
                lineTo(8.59f, 7.41f)
                lineTo(10f, 6f)
                lineToRelative(6f, 6f)
                lineToRelative(-6f, 6f)
                lineToRelative(-1.41f, -1.41f)
                close()
            }
        }
    }

    /** lock.svg */
    val Lock: ImageVector by lazy {
        icon("Lock") {
            filled {
                moveTo(18f, 8f)
                horizontalLineToRelative(-1f)
                verticalLineTo(6f)
                curveToRelative(0f, -2.76f, -2.24f, -5f, -5f, -5f)
                reflectiveCurveTo(7f, 3.24f, 7f, 6f)
                verticalLineToRelative(2f)
                horizontalLineTo(6f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(10f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(12f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(10f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(9f, 6f)
                curveToRelative(0f, -1.66f, 1.34f, -3f, 3f, -3f)
                reflectiveCurveToRelative(3f, 1.34f, 3f, 3f)
                verticalLineToRelative(2f)
                horizontalLineTo(9f)
                verticalLineTo(6f)
                close()
                moveToRelative(9f, 14f)
                horizontalLineTo(6f)
                verticalLineTo(10f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(10f)
                close()
                moveToRelative(-6f, -3f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                close()
            }
        }
    }

    /** menu.svg */
    val Menu: ImageVector by lazy {
        icon("Menu") {
            filled {
                moveTo(3f, 18f)
                horizontalLineToRelative(18f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                close()
                moveToRelative(0f, -5f)
                horizontalLineToRelative(18f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                close()
                moveToRelative(0f, -7f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(18f)
                verticalLineTo(6f)
                horizontalLineTo(3f)
                close()
            }
        }
    }

    /** menu_book.svg */
    val MenuBook: ImageVector by lazy {
        icon("MenuBook", autoMirror = true) {
            filled {
                moveTo(21f, 5f)
                curveToRelative(-1.11f, -0.35f, -2.33f, -0.5f, -3.5f, -0.5f)
                curveToRelative(-1.95f, 0f, -4.05f, 0.4f, -5.5f, 1.5f)
                curveToRelative(-1.45f, -1.1f, -3.55f, -1.5f, -5.5f, -1.5f)
                reflectiveCurveTo(2.45f, 4.9f, 1f, 6f)
                verticalLineToRelative(14.65f)
                curveToRelative(0f, 0.25f, 0.25f, 0.5f, 0.5f, 0.5f)
                curveToRelative(0.1f, 0f, 0.15f, -0.05f, 0.25f, -0.05f)
                curveTo(3.1f, 20.45f, 5.05f, 20f, 6.5f, 20f)
                curveToRelative(1.95f, 0f, 4.05f, 0.4f, 5.5f, 1.5f)
                curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
                curveToRelative(1.65f, 0f, 3.35f, 0.3f, 4.75f, 1.05f)
                curveToRelative(0.1f, 0.05f, 0.15f, 0.05f, 0.25f, 0.05f)
                curveToRelative(0.25f, 0f, 0.5f, -0.25f, 0.5f, -0.5f)
                verticalLineTo(6f)
                curveToRelative(-0.6f, -0.45f, -1.25f, -0.75f, -2f, -1f)
                close()
                moveToRelative(0f, 13.5f)
                curveToRelative(-1.1f, -0.35f, -2.3f, -0.5f, -3.5f, -0.5f)
                curveToRelative(-1.7f, 0f, -4.15f, 0.65f, -5.5f, 1.5f)
                verticalLineTo(8f)
                curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
                curveToRelative(1.2f, 0f, 2.4f, 0.15f, 3.5f, 0.5f)
                verticalLineToRelative(11.5f)
                close()
            }
            filled {
                moveTo(17.5f, 10.5f)
                curveToRelative(0.88f, 0f, 1.73f, 0.09f, 2.5f, 0.26f)
                verticalLineTo(9.24f)
                curveToRelative(-0.79f, -0.15f, -1.64f, -0.24f, -2.5f, -0.24f)
                curveToRelative(-1.7f, 0f, -3.24f, 0.29f, -4.5f, 0.83f)
                verticalLineToRelative(1.66f)
                curveToRelative(1.13f, -0.64f, 2.7f, -0.99f, 4.5f, -0.99f)
                close()
                moveTo(13f, 12.49f)
                verticalLineToRelative(1.66f)
                curveToRelative(1.13f, -0.64f, 2.7f, -0.99f, 4.5f, -0.99f)
                curveToRelative(0.88f, 0f, 1.73f, 0.09f, 2.5f, 0.26f)
                verticalLineTo(11.9f)
                curveToRelative(-0.79f, -0.15f, -1.64f, -0.24f, -2.5f, -0.24f)
                curveToRelative(-1.7f, 0f, -3.24f, 0.3f, -4.5f, 0.83f)
                close()
                moveToRelative(4.5f, 1.84f)
                curveToRelative(-1.7f, 0f, -3.24f, 0.29f, -4.5f, 0.83f)
                verticalLineToRelative(1.66f)
                curveToRelative(1.13f, -0.64f, 2.7f, -0.99f, 4.5f, -0.99f)
                curveToRelative(0.88f, 0f, 1.73f, 0.09f, 2.5f, 0.26f)
                verticalLineToRelative(-1.52f)
                curveToRelative(-0.79f, -0.16f, -1.64f, -0.24f, -2.5f, -0.24f)
                close()
            }
        }
    }

    /** more_vert.svg */
    val MoreVert: ImageVector by lazy {
        icon("MoreVert") {
            filled {
                moveTo(12f, 8f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                close()
                moveToRelative(0f, 2f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                close()
                moveToRelative(0f, 6f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                close()
            }
        }
    }

    /** music_note.svg */
    val MusicNote: ImageVector by lazy {
        icon("MusicNote") {
            filled {
                moveToRelative(12f, 3f)
                lineToRelative(0.01f, 10.55f)
                curveToRelative(-0.59f, -0.34f, -1.27f, -0.55f, -2f, -0.55f)
                arcToRelative(4.001f, 4.001f, 0f, true, false, 0f, 8f)
                curveToRelative(2.22f, 0f, 3.99f, -1.79f, 3.99f, -4f)
                verticalLineTo(7f)
                horizontalLineToRelative(4f)
                verticalLineTo(3f)
                horizontalLineToRelative(-6f)
                close()
                moveToRelative(-1.99f, 16f)
                curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
                reflectiveCurveToRelative(0.9f, -2f, 2f, -2f)
                reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
                reflectiveCurveToRelative(-0.9f, 2f, -2f, 2f)
                close()
            }
        }
    }

    /** nightlight.svg */
    val Nightlight: ImageVector by lazy {
        icon("Nightlight") {
            filled {
                moveTo(14f, 4f)
                curveToRelative(0.34f, 0f, 0.68f, 0.02f, 1.01f, 0.07f)
                curveTo(13.1f, 6.23f, 12f, 9.05f, 12f, 12f)
                reflectiveCurveToRelative(1.1f, 5.77f, 3.01f, 7.93f)
                curveToRelative(-0.33f, 0.05f, -0.67f, 0.07f, -1.01f, 0.07f)
                curveToRelative(-4.41f, 0f, -8f, -3.59f, -8f, -8f)
                reflectiveCurveToRelative(3.59f, -8f, 8f, -8f)
                moveToRelative(0f, -2f)
                curveTo(8.48f, 2f, 4f, 6.48f, 4f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                curveToRelative(1.82f, 0f, 3.53f, -0.5f, 5f, -1.35f)
                curveToRelative(-2.99f, -1.73f, -5f, -4.95f, -5f, -8.65f)
                reflectiveCurveToRelative(2.01f, -6.92f, 5f, -8.65f)
                arcTo(9.973f, 9.973f, 0f, false, false, 14f, 2f)
                close()
            }
        }
    }

    /** notifications.svg */
    val Notifications: ImageVector by lazy {
        icon("Notifications") {
            filled {
                moveTo(12f, 22f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                horizontalLineToRelative(-4f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                close()
                moveToRelative(6f, -6f)
                verticalLineToRelative(-5f)
                curveToRelative(0f, -3.07f, -1.63f, -5.64f, -4.5f, -6.32f)
                verticalLineTo(4f)
                curveToRelative(0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f)
                reflectiveCurveToRelative(-1.5f, 0.67f, -1.5f, 1.5f)
                verticalLineToRelative(0.68f)
                curveTo(7.64f, 5.36f, 6f, 7.92f, 6f, 11f)
                verticalLineToRelative(5f)
                lineToRelative(-2f, 2f)
                verticalLineToRelative(1f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(-1f)
                lineToRelative(-2f, -2f)
                close()
                moveToRelative(-2f, 1f)
                horizontalLineTo(8f)
                verticalLineToRelative(-6f)
                curveToRelative(0f, -2.48f, 1.51f, -4.5f, 4f, -4.5f)
                reflectiveCurveToRelative(4f, 2.02f, 4f, 4.5f)
                verticalLineToRelative(6f)
                close()
            }
        }
    }

    /** palette.svg */
    val Palette: ImageVector by lazy {
        icon("Palette") {
            filled {
                moveTo(12f, 22f)
                curveTo(6.49f, 22f, 2f, 17.51f, 2f, 12f)
                reflectiveCurveTo(6.49f, 2f, 12f, 2f)
                reflectiveCurveToRelative(10f, 4.04f, 10f, 9f)
                curveToRelative(0f, 3.31f, -2.69f, 6f, -6f, 6f)
                horizontalLineToRelative(-1.77f)
                curveToRelative(-0.28f, 0f, -0.5f, 0.22f, -0.5f, 0.5f)
                curveToRelative(0f, 0.12f, 0.05f, 0.23f, 0.13f, 0.33f)
                curveToRelative(0.41f, 0.47f, 0.64f, 1.06f, 0.64f, 1.67f)
                arcTo(2.5f, 2.5f, 0f, false, true, 12f, 22f)
                close()
                moveToRelative(0f, -18f)
                curveToRelative(-4.41f, 0f, -8f, 3.59f, -8f, 8f)
                reflectiveCurveToRelative(3.59f, 8f, 8f, 8f)
                curveToRelative(0.28f, 0f, 0.5f, -0.22f, 0.5f, -0.5f)
                arcToRelative(0.54f, 0.54f, 0f, false, false, -0.14f, -0.35f)
                curveToRelative(-0.41f, -0.46f, -0.63f, -1.05f, -0.63f, -1.65f)
                arcToRelative(2.5f, 2.5f, 0f, false, true, 2.5f, -2.5f)
                horizontalLineTo(16f)
                curveToRelative(2.21f, 0f, 4f, -1.79f, 4f, -4f)
                curveToRelative(0f, -3.86f, -3.59f, -7f, -8f, -7f)
                close()
            }
            filled {
                moveTo(5f, 11.5f)
                arcToRelative(1.5f, 1.5f, 0f, true, false, 3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, true, false, -3f, 0f)
                close()
            }
            filled {
                moveTo(8f, 7.5f)
                arcToRelative(1.5f, 1.5f, 0f, true, false, 3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, true, false, -3f, 0f)
                close()
            }
            filled {
                moveTo(13f, 7.5f)
                arcToRelative(1.5f, 1.5f, 0f, true, false, 3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, true, false, -3f, 0f)
                close()
            }
            filled {
                moveTo(16f, 11.5f)
                arcToRelative(1.5f, 1.5f, 0f, true, false, 3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, true, false, -3f, 0f)
                close()
            }
        }
    }

    /** pause.svg */
    val Pause: ImageVector by lazy {
        icon("Pause") {
            filled {
                moveTo(6f, 19f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineTo(6f)
                verticalLineToRelative(14f)
                close()
                moveToRelative(8f, -14f)
                verticalLineToRelative(14f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineToRelative(-4f)
                close()
            }
        }
    }

    /** people.svg */
    val People: ImageVector by lazy {
        icon("People") {
            filled {
                moveTo(9f, 13.75f)
                curveToRelative(-2.34f, 0f, -7f, 1.17f, -7f, 3.5f)
                verticalLineTo(19f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-1.75f)
                curveToRelative(0f, -2.33f, -4.66f, -3.5f, -7f, -3.5f)
                close()
                moveTo(4.34f, 17f)
                curveToRelative(0.84f, -0.58f, 2.87f, -1.25f, 4.66f, -1.25f)
                reflectiveCurveToRelative(3.82f, 0.67f, 4.66f, 1.25f)
                horizontalLineTo(4.34f)
                close()
                moveTo(9f, 12f)
                curveToRelative(1.93f, 0f, 3.5f, -1.57f, 3.5f, -3.5f)
                reflectiveCurveTo(10.93f, 5f, 9f, 5f)
                reflectiveCurveTo(5.5f, 6.57f, 5.5f, 8.5f)
                reflectiveCurveTo(7.07f, 12f, 9f, 12f)
                close()
                moveToRelative(0f, -5f)
                curveToRelative(0.83f, 0f, 1.5f, 0.67f, 1.5f, 1.5f)
                reflectiveCurveTo(9.83f, 10f, 9f, 10f)
                reflectiveCurveToRelative(-1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(8.17f, 7f, 9f, 7f)
                close()
                moveToRelative(7.04f, 6.81f)
                curveToRelative(1.16f, 0.84f, 1.96f, 1.96f, 1.96f, 3.44f)
                verticalLineTo(19f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-1.75f)
                curveToRelative(0f, -2.02f, -3.5f, -3.17f, -5.96f, -3.44f)
                close()
                moveTo(15f, 12f)
                curveToRelative(1.93f, 0f, 3.5f, -1.57f, 3.5f, -3.5f)
                reflectiveCurveTo(16.93f, 5f, 15f, 5f)
                curveToRelative(-0.54f, 0f, -1.04f, 0.13f, -1.5f, 0.35f)
                curveToRelative(0.63f, 0.89f, 1f, 1.98f, 1f, 3.15f)
                reflectiveCurveToRelative(-0.37f, 2.26f, -1f, 3.15f)
                curveToRelative(0.46f, 0.22f, 0.96f, 0.35f, 1.5f, 0.35f)
                close()
            }
        }
    }

    /** play_arrow.svg */
    val PlayArrow: ImageVector by lazy {
        icon("PlayArrow") {
            filled {
                moveTo(10f, 8.64f)
                lineTo(15.27f, 12f)
                lineTo(10f, 15.36f)
                verticalLineTo(8.64f)
                moveTo(8f, 5f)
                verticalLineToRelative(14f)
                lineToRelative(11f, -7f)
                lineTo(8f, 5f)
                close()
            }
        }
    }

    /** refresh.svg */
    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            filled {
                moveTo(17.65f, 6.35f)
                arcTo(7.958f, 7.958f, 0f, false, false, 12f, 4f)
                curveToRelative(-4.42f, 0f, -7.99f, 3.58f, -7.99f, 8f)
                reflectiveCurveToRelative(3.57f, 8f, 7.99f, 8f)
                curveToRelative(3.73f, 0f, 6.84f, -2.55f, 7.73f, -6f)
                horizontalLineToRelative(-2.08f)
                arcTo(5.99f, 5.99f, 0f, false, true, 12f, 18f)
                curveToRelative(-3.31f, 0f, -6f, -2.69f, -6f, -6f)
                reflectiveCurveToRelative(2.69f, -6f, 6f, -6f)
                curveToRelative(1.66f, 0f, 3.14f, 0.69f, 4.22f, 1.78f)
                lineTo(13f, 11f)
                horizontalLineToRelative(7f)
                verticalLineTo(4f)
                lineToRelative(-2.35f, 2.35f)
                close()
            }
        }
    }

    /** remove.svg */
    val Remove: ImageVector by lazy {
        icon("Remove") {
            filled {
                moveTo(19f, 13f)
                horizontalLineTo(5f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(2f)
                close()
            }
        }
    }

    /** restore.svg */
    val Restore: ImageVector by lazy {
        icon("Restore") {
            filled {
                moveTo(13f, 3f)
                arcToRelative(9f, 9f, 0f, false, false, -9f, 9f)
                horizontalLineTo(1f)
                lineToRelative(4f, 3.99f)
                lineTo(9f, 12f)
                horizontalLineTo(6f)
                curveToRelative(0f, -3.87f, 3.13f, -7f, 7f, -7f)
                reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
                reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f)
                curveToRelative(-1.93f, 0f, -3.68f, -0.79f, -4.94f, -2.06f)
                lineToRelative(-1.42f, 1.42f)
                arcTo(8.954f, 8.954f, 0f, false, false, 13f, 21f)
                arcToRelative(9f, 9f, 0f, false, false, 0f, -18f)
                close()
                moveToRelative(-1f, 5f)
                verticalLineToRelative(5f)
                lineToRelative(4.25f, 2.52f)
                lineToRelative(0.77f, -1.28f)
                lineToRelative(-3.52f, -2.09f)
                verticalLineTo(8f)
                close()
            }
        }
    }

    /** schedule.svg */
    val Schedule: ImageVector by lazy {
        icon("Schedule") {
            filled {
                moveTo(11.99f, 2f)
                curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.47f, 10f, 9.99f, 10f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
                close()
                moveTo(12f, 20f)
                curveToRelative(-4.42f, 0f, -8f, -3.58f, -8f, -8f)
                reflectiveCurveToRelative(3.58f, -8f, 8f, -8f)
                reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
                reflectiveCurveToRelative(-3.58f, 8f, -8f, 8f)
                close()
                moveToRelative(0.5f, -13f)
                horizontalLineTo(11f)
                verticalLineToRelative(6f)
                lineToRelative(5.25f, 3.15f)
                lineToRelative(0.75f, -1.23f)
                lineToRelative(-4.5f, -2.67f)
                close()
            }
        }
    }

    /** school.svg */
    val School: ImageVector by lazy {
        icon("School") {
            filled {
                moveTo(12f, 3f)
                lineTo(1f, 9f)
                lineToRelative(4f, 2.18f)
                verticalLineToRelative(6f)
                lineTo(12f, 21f)
                lineToRelative(7f, -3.82f)
                verticalLineToRelative(-6f)
                lineToRelative(2f, -1.09f)
                verticalLineTo(17f)
                horizontalLineToRelative(2f)
                verticalLineTo(9f)
                lineTo(12f, 3f)
                close()
                moveToRelative(6.82f, 6f)
                lineTo(12f, 12.72f)
                lineTo(5.18f, 9f)
                lineTo(12f, 5.28f)
                lineTo(18.82f, 9f)
                close()
                moveTo(17f, 15.99f)
                lineToRelative(-5f, 2.73f)
                lineToRelative(-5f, -2.73f)
                verticalLineToRelative(-3.72f)
                lineTo(12f, 15f)
                lineToRelative(5f, -2.73f)
                verticalLineToRelative(3.72f)
                close()
            }
        }
    }

    /** settings.svg */
    val Settings: ImageVector by lazy {
        icon("Settings") {
            filled {
                moveTo(19.43f, 12.98f)
                curveToRelative(0.04f, -0.32f, 0.07f, -0.64f, 0.07f, -0.98f)
                curveToRelative(0f, -0.34f, -0.03f, -0.66f, -0.07f, -0.98f)
                lineToRelative(2.11f, -1.65f)
                curveToRelative(0.19f, -0.15f, 0.24f, -0.42f, 0.12f, -0.64f)
                lineToRelative(-2f, -3.46f)
                arcToRelative(0.5f, 0.5f, 0f, false, false, -0.61f, -0.22f)
                lineToRelative(-2.49f, 1f)
                curveToRelative(-0.52f, -0.4f, -1.08f, -0.73f, -1.69f, -0.98f)
                lineToRelative(-0.38f, -2.65f)
                arcTo(0.488f, 0.488f, 0f, false, false, 14f, 2f)
                horizontalLineToRelative(-4f)
                curveToRelative(-0.25f, 0f, -0.46f, 0.18f, -0.49f, 0.42f)
                lineToRelative(-0.38f, 2.65f)
                curveToRelative(-0.61f, 0.25f, -1.17f, 0.59f, -1.69f, 0.98f)
                lineToRelative(-2.49f, -1f)
                arcToRelative(0.566f, 0.566f, 0f, false, false, -0.18f, -0.03f)
                curveToRelative(-0.17f, 0f, -0.34f, 0.09f, -0.43f, 0.25f)
                lineToRelative(-2f, 3.46f)
                curveToRelative(-0.13f, 0.22f, -0.07f, 0.49f, 0.12f, 0.64f)
                lineToRelative(2.11f, 1.65f)
                curveToRelative(-0.04f, 0.32f, -0.07f, 0.65f, -0.07f, 0.98f)
                curveToRelative(0f, 0.33f, 0.03f, 0.66f, 0.07f, 0.98f)
                lineToRelative(-2.11f, 1.65f)
                curveToRelative(-0.19f, 0.15f, -0.24f, 0.42f, -0.12f, 0.64f)
                lineToRelative(2f, 3.46f)
                arcToRelative(0.5f, 0.5f, 0f, false, false, 0.61f, 0.22f)
                lineToRelative(2.49f, -1f)
                curveToRelative(0.52f, 0.4f, 1.08f, 0.73f, 1.69f, 0.98f)
                lineToRelative(0.38f, 2.65f)
                curveToRelative(0.03f, 0.24f, 0.24f, 0.42f, 0.49f, 0.42f)
                horizontalLineToRelative(4f)
                curveToRelative(0.25f, 0f, 0.46f, -0.18f, 0.49f, -0.42f)
                lineToRelative(0.38f, -2.65f)
                curveToRelative(0.61f, -0.25f, 1.17f, -0.59f, 1.69f, -0.98f)
                lineToRelative(2.49f, 1f)
                curveToRelative(0.06f, 0.02f, 0.12f, 0.03f, 0.18f, 0.03f)
                curveToRelative(0.17f, 0f, 0.34f, -0.09f, 0.43f, -0.25f)
                lineToRelative(2f, -3.46f)
                curveToRelative(0.12f, -0.22f, 0.07f, -0.49f, -0.12f, -0.64f)
                lineToRelative(-2.11f, -1.65f)
                close()
                moveToRelative(-1.98f, -1.71f)
                curveToRelative(0.04f, 0.31f, 0.05f, 0.52f, 0.05f, 0.73f)
                curveToRelative(0f, 0.21f, -0.02f, 0.43f, -0.05f, 0.73f)
                lineToRelative(-0.14f, 1.13f)
                lineToRelative(0.89f, 0.7f)
                lineToRelative(1.08f, 0.84f)
                lineToRelative(-0.7f, 1.21f)
                lineToRelative(-1.27f, -0.51f)
                lineToRelative(-1.04f, -0.42f)
                lineToRelative(-0.9f, 0.68f)
                curveToRelative(-0.43f, 0.32f, -0.84f, 0.56f, -1.25f, 0.73f)
                lineToRelative(-1.06f, 0.43f)
                lineToRelative(-0.16f, 1.13f)
                lineToRelative(-0.2f, 1.35f)
                horizontalLineToRelative(-1.4f)
                lineToRelative(-0.19f, -1.35f)
                lineToRelative(-0.16f, -1.13f)
                lineToRelative(-1.06f, -0.43f)
                curveToRelative(-0.43f, -0.18f, -0.83f, -0.41f, -1.23f, -0.71f)
                lineToRelative(-0.91f, -0.7f)
                lineToRelative(-1.06f, 0.43f)
                lineToRelative(-1.27f, 0.51f)
                lineToRelative(-0.7f, -1.21f)
                lineToRelative(1.08f, -0.84f)
                lineToRelative(0.89f, -0.7f)
                lineToRelative(-0.14f, -1.13f)
                curveToRelative(-0.03f, -0.31f, -0.05f, -0.54f, -0.05f, -0.74f)
                reflectiveCurveToRelative(0.02f, -0.43f, 0.05f, -0.73f)
                lineToRelative(0.14f, -1.13f)
                lineToRelative(-0.89f, -0.7f)
                lineToRelative(-1.08f, -0.84f)
                lineToRelative(0.7f, -1.21f)
                lineToRelative(1.27f, 0.51f)
                lineToRelative(1.04f, 0.42f)
                lineToRelative(0.9f, -0.68f)
                curveToRelative(0.43f, -0.32f, 0.84f, -0.56f, 1.25f, -0.73f)
                lineToRelative(1.06f, -0.43f)
                lineToRelative(0.16f, -1.13f)
                lineToRelative(0.2f, -1.35f)
                horizontalLineToRelative(1.39f)
                lineToRelative(0.19f, 1.35f)
                lineToRelative(0.16f, 1.13f)
                lineToRelative(1.06f, 0.43f)
                curveToRelative(0.43f, 0.18f, 0.83f, 0.41f, 1.23f, 0.71f)
                lineToRelative(0.91f, 0.7f)
                lineToRelative(1.06f, -0.43f)
                lineToRelative(1.27f, -0.51f)
                lineToRelative(0.7f, 1.21f)
                lineToRelative(-1.07f, 0.85f)
                lineToRelative(-0.89f, 0.7f)
                lineToRelative(0.14f, 1.13f)
                close()
                moveTo(12f, 8f)
                curveToRelative(-2.21f, 0f, -4f, 1.79f, -4f, 4f)
                reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
                reflectiveCurveToRelative(4f, -1.79f, 4f, -4f)
                reflectiveCurveToRelative(-1.79f, -4f, -4f, -4f)
                close()
                moveToRelative(0f, 6f)
                curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
                reflectiveCurveToRelative(0.9f, -2f, 2f, -2f)
                reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
                reflectiveCurveToRelative(-0.9f, 2f, -2f, 2f)
                close()
            }
        }
    }

    /** sports_esports.svg */
    val SportsEsports: ImageVector by lazy {
        icon("SportsEsports") {
            filled {
                moveToRelative(21.58f, 16.09f)
                lineToRelative(-1.09f, -7.66f)
                arcTo(3.996f, 3.996f, 0f, false, false, 16.53f, 5f)
                horizontalLineTo(7.47f)
                curveTo(5.48f, 5f, 3.79f, 6.46f, 3.51f, 8.43f)
                lineToRelative(-1.09f, 7.66f)
                arcToRelative(2.545f, 2.545f, 0f, false, false, 4.32f, 2.16f)
                lineTo(9f, 16f)
                horizontalLineToRelative(6f)
                lineToRelative(2.25f, 2.25f)
                curveToRelative(0.48f, 0.48f, 1.13f, 0.75f, 1.8f, 0.75f)
                curveToRelative(1.56f, 0f, 2.75f, -1.37f, 2.53f, -2.91f)
                close()
                moveToRelative(-2.1f, 0.72f)
                arcToRelative(0.54f, 0.54f, 0f, false, true, -0.42f, 0.19f)
                curveToRelative(-0.15f, 0f, -0.29f, -0.06f, -0.39f, -0.16f)
                lineTo(15.83f, 14f)
                horizontalLineTo(8.17f)
                lineToRelative(-2.84f, 2.84f)
                curveToRelative(-0.1f, 0.1f, -0.24f, 0.16f, -0.39f, 0.16f)
                arcToRelative(0.54f, 0.54f, 0f, false, true, -0.42f, -0.19f)
                arcToRelative(0.52f, 0.52f, 0f, false, true, -0.13f, -0.44f)
                lineToRelative(1.09f, -7.66f)
                curveTo(5.63f, 7.74f, 6.48f, 7f, 7.47f, 7f)
                horizontalLineToRelative(9.06f)
                curveToRelative(0.99f, 0f, 1.84f, 0.74f, 1.98f, 1.72f)
                lineToRelative(1.09f, 7.66f)
                curveToRelative(0.03f, 0.2f, -0.05f, 0.34f, -0.12f, 0.43f)
                close()
            }
            filled {
                moveTo(9f, 8f)
                horizontalLineTo(8f)
                verticalLineToRelative(2f)
                horizontalLineTo(6f)
                verticalLineToRelative(1f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(1f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-1f)
                horizontalLineTo(9f)
                close()
            }
            filled {
                moveTo(16f, 12f)
                arcToRelative(1f, 1f, 0f, true, false, 2f, 0f)
                arcToRelative(1f, 1f, 0f, true, false, -2f, 0f)
                close()
            }
            filled {
                moveTo(14f, 9f)
                arcToRelative(1f, 1f, 0f, true, false, 2f, 0f)
                arcToRelative(1f, 1f, 0f, true, false, -2f, 0f)
                close()
            }
        }
    }

    /** stop.svg */
    val Stop: ImageVector by lazy {
        icon("Stop") {
            filled {
                moveTo(16f, 8f)
                verticalLineToRelative(8f)
                horizontalLineTo(8f)
                verticalLineTo(8f)
                horizontalLineToRelative(8f)
                moveToRelative(2f, -2f)
                horizontalLineTo(6f)
                verticalLineToRelative(12f)
                horizontalLineToRelative(12f)
                verticalLineTo(6f)
                close()
            }
        }
    }

    /** swap_horiz.svg */
    val SwapHoriz: ImageVector by lazy {
        icon("SwapHoriz") {
            filled {
                moveTo(6.99f, 11f)
                lineTo(3f, 15f)
                lineToRelative(3.99f, 4f)
                verticalLineToRelative(-3f)
                horizontalLineTo(14f)
                verticalLineToRelative(-2f)
                horizontalLineTo(6.99f)
                verticalLineToRelative(-3f)
                close()
                moveTo(21f, 9f)
                lineToRelative(-3.99f, -4f)
                verticalLineToRelative(3f)
                horizontalLineTo(10f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(7.01f)
                verticalLineToRelative(3f)
                lineTo(21f, 9f)
                close()
            }
        }
    }

    /** today.svg */
    val Today: ImageVector by lazy {
        icon("Today") {
            filled {
                moveTo(19f, 3f)
                horizontalLineToRelative(-1f)
                verticalLineTo(1f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                horizontalLineTo(8f)
                verticalLineTo(1f)
                horizontalLineTo(6f)
                verticalLineToRelative(2f)
                horizontalLineTo(5f)
                arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
                verticalLineToRelative(14f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveToRelative(0f, 16f)
                horizontalLineTo(5f)
                verticalLineTo(9f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(10f)
                close()
                moveToRelative(0f, -12f)
                horizontalLineTo(5f)
                verticalLineTo(5f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(2f)
                close()
                moveTo(7f, 11f)
                horizontalLineToRelative(5f)
                verticalLineToRelative(5f)
                horizontalLineTo(7f)
                close()
            }
        }
    }

    /** upload.svg */
    val Upload: ImageVector by lazy {
        icon("Upload") {
            filled {
                moveTo(9f, 16f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(-6f)
                horizontalLineToRelative(4f)
                lineToRelative(-7f, -7f)
                lineToRelative(-7f, 7f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(6f)
                close()
                moveToRelative(3f, -10.17f)
                lineTo(14.17f, 8f)
                horizontalLineTo(13f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(-2f)
                verticalLineTo(8f)
                horizontalLineTo(9.83f)
                lineTo(12f, 5.83f)
                close()
                moveTo(5f, 18f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(2f)
                horizontalLineTo(5f)
                close()
            }
        }
    }

    /** view_agenda.svg */
    val ViewAgenda: ImageVector by lazy {
        icon("ViewAgenda") {
            filled {
                moveTo(19f, 13f)
                horizontalLineTo(5f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(4f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineToRelative(-4f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveToRelative(0f, 6f)
                horizontalLineTo(5f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(4f)
                close()
                moveToRelative(0f, -16f)
                horizontalLineTo(5f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(4f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveToRelative(0f, 6f)
                horizontalLineTo(5f)
                verticalLineTo(5f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(4f)
                close()
            }
        }
    }

    /** warning_amber.svg */
    val WarningAmber: ImageVector by lazy {
        icon("WarningAmber") {
            filled {
                moveTo(12f, 5.99f)
                lineTo(19.53f, 19f)
                horizontalLineTo(4.47f)
                lineTo(12f, 5.99f)
                moveTo(12f, 2f)
                lineTo(1f, 21f)
                horizontalLineToRelative(22f)
                lineTo(12f, 2f)
                close()
                moveToRelative(1f, 14f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2f)
                close()
                moveToRelative(0f, -6f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-4f)
                close()
            }
        }
    }

    /** wb_twilight.svg */
    val WbTwilight: ImageVector by lazy {
        icon("WbTwilight") {
            filled {
                moveToRelative(16.955f, 8.662f)
                lineToRelative(2.12f, -2.122f)
                lineToRelative(1.416f, 1.414f)
                lineToRelative(-2.121f, 2.122f)
                close()
                moveTo(2f, 18f)
                horizontalLineToRelative(20f)
                verticalLineToRelative(2f)
                horizontalLineTo(2f)
                close()
                moveToRelative(9f, -14f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(-2f)
                close()
                moveTo(3.543f, 7.925f)
                lineTo(4.957f, 6.51f)
                lineToRelative(2.121f, 2.12f)
                lineToRelative(-1.414f, 1.415f)
                close()
                moveTo(5f, 16f)
                horizontalLineToRelative(14f)
                curveToRelative(0f, -3.87f, -3.13f, -7f, -7f, -7f)
                reflectiveCurveToRelative(-7f, 3.13f, -7f, 7f)
                close()
            }
        }
    }

    /** weekend.svg */
    val Weekend: ImageVector by lazy {
        icon("Weekend") {
            filled {
                moveTo(21f, 9f)
                verticalLineTo(7f)
                curveToRelative(0f, -1.65f, -1.35f, -3f, -3f, -3f)
                horizontalLineTo(6f)
                curveTo(4.35f, 4f, 3f, 5.35f, 3f, 7f)
                verticalLineToRelative(2f)
                curveToRelative(-1.65f, 0f, -3f, 1.35f, -3f, 3f)
                verticalLineToRelative(5f)
                curveToRelative(0f, 1.65f, 1.35f, 3f, 3f, 3f)
                horizontalLineToRelative(18f)
                curveToRelative(1.65f, 0f, 3f, -1.35f, 3f, -3f)
                verticalLineToRelative(-5f)
                curveToRelative(0f, -1.65f, -1.35f, -3f, -3f, -3f)
                close()
                moveTo(5f, 7f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                horizontalLineToRelative(12f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                verticalLineToRelative(2.78f)
                curveToRelative(-0.61f, 0.55f, -1f, 1.34f, -1f, 2.22f)
                verticalLineToRelative(2f)
                horizontalLineTo(6f)
                verticalLineToRelative(-2f)
                curveToRelative(0f, -0.88f, -0.39f, -1.67f, -1f, -2.22f)
                verticalLineTo(7f)
                close()
                moveToRelative(17f, 10f)
                curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
                horizontalLineTo(3f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                verticalLineToRelative(-5f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(-4f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
                verticalLineToRelative(5f)
                close()
            }
        }
    }

    /** work.svg */
    val Work: ImageVector by lazy {
        icon("Work") {
            filled {
                moveTo(20f, 6f)
                horizontalLineToRelative(-4f)
                verticalLineTo(4f)
                curveToRelative(0f, -1.11f, -0.89f, -2f, -2f, -2f)
                horizontalLineToRelative(-4f)
                curveToRelative(-1.11f, 0f, -2f, 0.89f, -2f, 2f)
                verticalLineToRelative(2f)
                horizontalLineTo(4f)
                curveToRelative(-1.11f, 0f, -1.99f, 0.89f, -1.99f, 2f)
                lineTo(2f, 19f)
                curveToRelative(0f, 1.11f, 0.89f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.11f, 0f, 2f, -0.89f, 2f, -2f)
                verticalLineTo(8f)
                curveToRelative(0f, -1.11f, -0.89f, -2f, -2f, -2f)
                close()
                moveToRelative(-6f, 0f)
                horizontalLineToRelative(-4f)
                verticalLineTo(4f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(2f)
                close()
            }
        }
    }
}

private inline fun icon(name: String, autoMirror: Boolean = false, paths: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = "AppIcons.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    ).apply(paths).build()

/** One shape of an icon. Black like the Material icons; `Icon` tints it. */
private inline fun ImageVector.Builder.filled(shape: PathBuilder.() -> Unit) {
    path(fill = SolidColor(Color.Black), pathBuilder = shape)
}
