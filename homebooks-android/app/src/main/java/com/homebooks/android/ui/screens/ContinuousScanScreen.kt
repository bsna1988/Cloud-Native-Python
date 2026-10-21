package com.homebooks.android.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.ResultPoint
import com.homebooks.android.ui.viewmodel.BooksViewModel
import com.homebooks.android.ui.viewmodel.ScanResultItem
import com.homebooks.android.ui.viewmodel.ScanStatus
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CompoundBarcodeView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinuousScanScreen(
    viewModel: BooksViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Force portrait orientation
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            if (originalOrientation != null) {
                activity.requestedOrientation = originalOrientation
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var compoundBarcodeView: CompoundBarcodeView? by remember { mutableStateOf(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> compoundBarcodeView?.resume()
                Lifecycle.Event.ON_PAUSE -> compoundBarcodeView?.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            compoundBarcodeView?.pause()
        }
    }

    val activeScan by viewModel.activeScanStatus.observeAsState(null)
    val scanResults by viewModel.continuousScanResults.observeAsState(emptyList())
    val addedCount = scanResults.count { it.status == ScanStatus.ADDED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Continuous Bulk Scan") },
                navigationIcon = {
                    IconButton(onClick = {
                        compoundBarcodeView?.pause()
                        onBackClick()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Badge(modifier = Modifier.padding(end = 16.dp)) {
                        Text("Added: $addedCount")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Camera preview using ZXing CompoundBarcodeView
            AndroidView(
                factory = { ctx ->
                    CompoundBarcodeView(ctx).apply {
                        setStatusText("")
                        decodeContinuous(object : BarcodeCallback {
                            override fun barcodeResult(result: BarcodeResult?) {
                                result?.text?.let { isbn ->
                                    viewModel.enqueueScan(isbn)
                                }
                            }
                            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
                        })
                        resume()
                        compoundBarcodeView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    compoundBarcodeView = view
                }
            )

            // Overlay with active scan status and recent scans
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Active Scan Banner
                activeScan?.let { scan ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when (scan.status) {
                                ScanStatus.ADDED -> MaterialTheme.colorScheme.primaryContainer
                                ScanStatus.ALREADY_EXISTS -> MaterialTheme.colorScheme.tertiaryContainer
                                ScanStatus.NOT_FOUND, ScanStatus.INVALID -> MaterialTheme.colorScheme.surfaceVariant
                                ScanStatus.NETWORK_ERROR -> MaterialTheme.colorScheme.errorContainer
                                ScanStatus.LOOKING_UP -> MaterialTheme.colorScheme.secondaryContainer
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ISBN: ${scan.isbn}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = when (scan.status) {
                                        ScanStatus.LOOKING_UP -> "Looking up..."
                                        ScanStatus.ADDED -> "Added ✓"
                                        ScanStatus.ALREADY_EXISTS -> "Already in library"
                                        ScanStatus.NOT_FOUND -> "Not found"
                                        ScanStatus.NETWORK_ERROR -> "Network error"
                                        ScanStatus.INVALID -> "Invalid barcode"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when (scan.status) {
                                        ScanStatus.ADDED -> MaterialTheme.colorScheme.primary
                                        ScanStatus.ALREADY_EXISTS -> MaterialTheme.colorScheme.tertiary
                                        ScanStatus.NETWORK_ERROR -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            if (!scan.title.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = scan.title,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // Recent Scans History
                if (scanResults.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Recent Scans Session (${scanResults.size})",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(scanResults, key = { it.id }) { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = item.title ?: item.isbn,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1
                                        )
                                        Text(
                                            text = when (item.status) {
                                                ScanStatus.ADDED -> "Added"
                                                ScanStatus.ALREADY_EXISTS -> "Exists"
                                                ScanStatus.NOT_FOUND -> "Not Found"
                                                ScanStatus.NETWORK_ERROR -> "Error"
                                                ScanStatus.INVALID -> "Invalid"
                                                ScanStatus.LOOKING_UP -> "Lookup..."
                                            },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
