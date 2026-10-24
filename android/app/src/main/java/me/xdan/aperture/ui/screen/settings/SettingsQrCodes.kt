package me.xdan.aperture.ui.screen.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.xdan.aperture.ui.component.touch.Button
import androidx.tv.material3.MaterialTheme
import me.xdan.aperture.ui.component.touch.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

internal const val TMDB_URL = "https://www.themoviedb.org"

@Composable
internal fun LinkQrDialog(
    title: String,
    description: String,
    url: String,
    qrRows: List<String>,
    onDismiss: () -> Unit,
    nextLabel: String? = null,
    onNext: (() -> Unit)? = null
) {
    val closeRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { closeRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.width(760.dp),
            shape = RoundedCornerShape(28.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(34.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThemedQrCode(rows = qrRows)
                Column(
                    modifier = Modifier.width(400.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                    )
                    Text(
                        url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(modifier = Modifier.align(Alignment.End)) {
                        if (nextLabel != null && onNext != null) {
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.focusRequester(closeRequester)
                            ) { Text("Cancel") }
                            androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                            Button(onClick = onNext) { Text(nextLabel) }
                        } else {
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.focusRequester(closeRequester)
                            ) { Text("Done") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ThemedQrCode(rows: List<String>) {
    val accent = MaterialTheme.colorScheme.primary
    // Keep the code conventionally dark-on-light for reliable phone scanning,
    // while retaining the selected theme's hue in both its modules and frame.
    val moduleColour = androidx.compose.ui.graphics.Color(
        red = accent.red * 0.32f,
        green = accent.green * 0.32f,
        blue = accent.blue * 0.32f,
        alpha = 1f
    )
    Surface(
        modifier = Modifier.size(260.dp),
        shape = RoundedCornerShape(24.dp),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Box(Modifier.fillMaxSize().padding(10.dp)) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.White
                )
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val quietZone = 4
                    val totalModules = rows.size + quietZone * 2
                    val moduleSize = size.minDimension / totalModules
                    val startX = (size.width - moduleSize * totalModules) / 2f
                    val startY = (size.height - moduleSize * totalModules) / 2f
                    rows.forEachIndexed { y, row ->
                        row.forEachIndexed { x, value ->
                            if (value == '1') {
                                drawRoundRect(
                                    color = moduleColour,
                                    topLeft = Offset(
                                        startX + (x + quietZone) * moduleSize,
                                        startY + (y + quietZone) * moduleSize
                                    ),
                                    size = Size(moduleSize, moduleSize),
                                    cornerRadius = CornerRadius(moduleSize * 0.22f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal val TMDB_QR_ROWS = listOf(
    "111111100010011101100111101111111",
    "100000100011111011010000001000001",
    "101110101100001010011101001011101",
    "101110101111010100000101001011101",
    "101110101100010001100011101011101",
    "100000100110111000111100101000001",
    "111111101010101010101010101111111",
    "000000000011101011010111100000000",
    "111001111010001010111000110011100",
    "101101000110111110011010010111011",
    "100010110100000101001101000101011",
    "001011011011110000101110101010101",
    "111110100000100011011010001111111",
    "111110000101101111000001000100011",
    "000000110110011110101000000011010",
    "011110001000001010010001000101101",
    "110000110111000111011110001111010",
    "100101001110010011010110001011010",
    "010101101101010110111001010001100",
    "010000011100110011100000111011100",
    "101100100010001001011010100010100",
    "111000010000100100110101011110001",
    "011101111110101100111000100101101",
    "101100000101110011010011101000001",
    "111010111010101101011010111110101",
    "000000001110111010010100100011110",
    "111111101111010001000000101010100",
    "100000101010101010001100100010010",
    "101110100000011110001010111111000",
    "101110100101010100011111101100101",
    "101110101101000010110101001101111",
    "100000101111101000011111111111001",
    "111111101101010110010101110110000"
)

