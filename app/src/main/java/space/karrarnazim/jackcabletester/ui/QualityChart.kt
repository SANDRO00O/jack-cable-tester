package space.karrarnazim.jackcabletester.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import java.util.Locale

/**
 * A live line chart of the cable quality score over the course of the test.
 * Y axis is fixed 0-100%, X axis is "packets received so far" — it grows
 * left to right as [history] gets longer.
 */
@Composable
fun LiveQualityChart(history: List<Float>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Box(modifier = modifier) {
        if (history.size < 2) {
            Text(
                "Chart will appear once packets start arriving",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Reference lines at 25/50/75/100%.
                for (fraction in listOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f)) {
                    val y = size.height * (1f - fraction)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                }

                val stepX = size.width / (history.size - 1).coerceAtLeast(1)
                val path = Path()
                history.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = size.height * (1f - (value.coerceIn(0f, 100f) / 100f))
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path = path, color = lineColor, style = Stroke(width = 5f))
            }
        }
    }
}

/** Formats a percentage using Latin digits regardless of the device's locale. */
fun formatPercent(value: Float): String = String.format(Locale.US, "%.1f", value)
