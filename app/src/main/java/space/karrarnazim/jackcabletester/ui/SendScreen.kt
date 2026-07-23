package space.karrarnazim.jackcabletester.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import space.karrarnazim.jackcabletester.audio.AudioTransmitter
import space.karrarnazim.jackcabletester.data.TestFileHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val transmitter = remember { AudioTransmitter() }
    
    var numPackets by remember { mutableStateOf(100f) }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var isTransmitting by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }

    val createDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val success = TestFileHelper.generateTestFile(context, uri, numPackets.toInt())
            if (success) {
                currentUri = uri
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transmitter") },
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
                "Step 1: Generate Test File",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text("Number of Packets: ${numPackets.toInt()}")
            Slider(
                value = numPackets,
                onValueChange = { numPackets = it },
                valueRange = 10f..1000f,
                steps = 99
            )
            
            Button(
                onClick = { createDocLauncher.launch("jack_test_file.jct") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate & Save Test File")
            }

            if (currentUri != null) {
                Text(
                    "File saved! Please transfer this file to the receiver device if it's a different phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    "Step 2: Transmit Audio",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                if (isTransmitting) {
                    LinearProgressIndicator(
                        progress = { if (total > 0) progress.toFloat() / total.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Sending packet $progress / $total")
                    
                    Button(
                        onClick = { 
                            transmitter.stop()
                            isTransmitting = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Stop Transmission")
                    }
                } else {
                    Button(
                        onClick = {
                            isTransmitting = true
                            scope.launch {
                                transmitter.transmit(currentUri!!, context) { p, t ->
                                    progress = p
                                    total = t
                                }
                                isTransmitting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start Transmission")
                    }
                }
            }
        }
    }
}
