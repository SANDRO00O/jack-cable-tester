package space.karrarnazim.jackcabletester.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import space.karrarnazim.jackcabletester.audio.AudioReceiver
import space.karrarnazim.jackcabletester.data.CableGrade
import space.karrarnazim.jackcabletester.data.TestFile
import space.karrarnazim.jackcabletester.data.TestFileHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val receiver = remember { AudioReceiver() }
    val stats by receiver.stats.collectAsStateWithLifecycle()

    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var loadedTestFile by remember { mutableStateOf<TestFile?>(null) }

    val openDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            currentUri = uri
            val tf = TestFileHelper.loadTestFile(context, uri)
            loadedTestFile = tf
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receiver") },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InfoBox(
                "This device listens on the microphone (headphone-in via the " +
                    "cable being tested) and decodes the tone signal back into " +
                    "packets, checking each one against the same reference file " +
                    "the transmitter used."
            )

            Text(
                "Step 1: Select Reference File",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Pick the exact .jct file the transmitter generated (transfer it " +
                    "over first if this is a different phone). It's the answer " +
                    "key this device checks incoming audio against.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { openDocLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loadedTestFile != null) "Change Test File" else "Select Test File (.jct)")
            }

            if (loadedTestFile != null) {
                Text(
                    "File loaded! Expected Packets: ${loadedTestFile!!.numPackets}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    "Step 2: Listen for Audio",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Plug the cable into this device's mic/headset port and start " +
                        "the transmitter on the other end (or the same phone, " +
                        "looped through the cable). Results update live below as " +
                        "packets arrive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (stats.isListening) {
                    Button(
                        onClick = { receiver.stop() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stop Listening")
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                receiver.startListening(loadedTestFile!!)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start Listening")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val successRate = if (stats.totalExpected > 0) {
                    (stats.validReceived.toFloat() / stats.totalExpected.toFloat()) * 100f
                } else 0f
                val grade = CableGrade.forScore(successRate)

                // Results
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Live Results", style = MaterialTheme.typography.titleLarge)

                        StatRow(
                            "Expected Packets",
                            stats.totalExpected.toString(),
                            explanation = "How many packets the reference file contains in total."
                        )
                        StatRow(
                            "Valid Received",
                            stats.validReceived.toString(),
                            color = MaterialTheme.colorScheme.primary,
                            explanation = "Packets that arrived intact and matched the reference exactly."
                        )
                        StatRow(
                            "Data Mismatches",
                            stats.dataMismatches.toString(),
                            color = MaterialTheme.colorScheme.error,
                            explanation = "Packets that passed the CRC check but whose content didn't match — usually a sign of a decoding glitch."
                        )
                        StatRow(
                            "CRC Errors",
                            stats.crcErrors.toString(),
                            color = MaterialTheme.colorScheme.error,
                            explanation = "Packets that arrived corrupted (checksum didn't match) — points to signal noise or a bad connection."
                        )

                        val missed = stats.totalExpected - stats.validReceived - stats.dataMismatches
                        StatRow(
                            "Missed Packets",
                            missed.toString(),
                            explanation = "Packets that never arrived at all — dropouts in the cable or audio path."
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        LiveQualityChart(
                            history = stats.history,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Cable Quality Score",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${formatPercent(successRate)}%",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (successRate > 95f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    grade,
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = gradeColor(grade)
                                )
                                Text(
                                    CableGrade.description(grade),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatRow(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    explanation: String? = null
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = color)
        }
        if (explanation != null) {
            Text(
                explanation,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A small dismissible-looking callout used to explain what a screen does. */
@Composable
fun InfoBox(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}

private fun gradeColor(grade: String): Color = when {
    grade.startsWith("A") -> Color(0xFF2E7D32)
    grade.startsWith("B") -> Color(0xFF558B2F)
    grade.startsWith("C") -> Color(0xFFF9A825)
    grade.startsWith("D") -> Color(0xFFEF6C00)
    else -> Color(0xFFC62828)
}
