package space.karrarnazim.jackcabletester.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import space.karrarnazim.jackcabletester.audio.AudioReceiver
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Step 1: Select Reference File",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
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
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    "Step 2: Listen for Audio",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
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

                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Results
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Live Results", style = MaterialTheme.typography.titleLarge)
                        
                        StatRow("Expected Packets", stats.totalExpected.toString())
                        StatRow("Valid Received", stats.validReceived.toString(), color = MaterialTheme.colorScheme.primary)
                        StatRow("Data Mismatches", stats.dataMismatches.toString(), color = MaterialTheme.colorScheme.error)
                        StatRow("CRC Errors", stats.crcErrors.toString(), color = MaterialTheme.colorScheme.error)
                        
                        val missed = stats.totalExpected - stats.validReceived - stats.dataMismatches
                        StatRow("Missed Packets", missed.toString())
                        
                        val successRate = if (stats.totalExpected > 0) {
                            (stats.validReceived.toFloat() / stats.totalExpected.toFloat()) * 100f
                        } else 0f
                        
                        Text(
                            "Cable Quality Score: ${"%.1f".format(successRate)}%",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (successRate > 95f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = color)
    }
}
