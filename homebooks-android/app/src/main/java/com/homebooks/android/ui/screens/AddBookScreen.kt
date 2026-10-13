package com.homebooks.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.homebooks.android.data.model.RecognizedBook
import com.homebooks.android.ui.viewmodel.BooksViewModel
import com.homebooks.android.ui.viewmodel.UiState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookScreen(
    viewModel: BooksViewModel,
    onBookAdded: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var releaseDate by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    var selectedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedModels by remember { mutableStateOf<List<Any>>(emptyList()) }

    var cameraFile by remember { mutableStateOf<File?>(null) }

    val recognitionState by viewModel.recognitionState.observeAsState(UiState.Idle)
    val errorMessage by viewModel.error.observeAsState(null)
    val snackbarHostState = remember { SnackbarHostState() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newFiles = uris.mapNotNull { uri ->
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val file = File(context.cacheDir, "book_img_${System.currentTimeMillis()}_${uri.lastPathSegment ?: "image"}.jpg")
                    inputStream?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    file
                } catch (_: Exception) {
                    null
                }
            }
            if (newFiles.isNotEmpty()) {
                selectedFiles = selectedFiles + newFiles
                selectedModels = selectedModels + uris
                viewModel.recognizeImages(selectedFiles)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraFile != null) {
            val file = cameraFile!!
            selectedFiles = selectedFiles + file
            selectedModels = selectedModels + file
            viewModel.recognizeImages(selectedFiles)
        }
    }

    LaunchedEffect(recognitionState) {
        when (val state = recognitionState) {
            is UiState.Success<*> -> {
                @Suppress("UNCHECKED_CAST")
                val recognizedBooks = state.data as? List<RecognizedBook>
                val best = recognizedBooks?.firstOrNull()
                if (best != null) {
                    if (title.isBlank()) title = best.title ?: title
                    if (author.isBlank()) author = best.author ?: author
                    if (barcode.isBlank()) barcode = best.barcode ?: barcode
                    if (releaseDate.isBlank()) releaseDate = best.release_date ?: releaseDate
                    if (description.isBlank()) description = best.description ?: description
                }
            }
            else -> {}
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Add Book") },
                navigationIcon = {
                    IconButton(onClick = {
                        title = ""
                        author = ""
                        barcode = ""
                        releaseDate = ""
                        description = ""
                        selectedFiles = emptyList()
                        selectedModels = emptyList()
                        viewModel.clearRecognitionState()
                        onBackClick()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gallery")
                }

                OutlinedButton(
                    onClick = {
                        val file = File(context.cacheDir, "book_cam_${System.currentTimeMillis()}.jpg")
                        cameraFile = file
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        cameraLauncher.launch(uri)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Camera")
                }
            }

            if (recognitionState is UiState.Loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Recognizing images...", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (selectedModels.isNotEmpty()) {
                Text("Selected Images (${selectedModels.size}):", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedModels.forEachIndexed { index, model ->
                        Box(
                            modifier = Modifier.size(90.dp)
                        ) {
                            AsyncImage(
                                model = model,
                                contentDescription = "Selected Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = {
                                    selectedFiles = selectedFiles.toMutableList().apply { removeAt(index) }
                                    selectedModels = selectedModels.toMutableList().apply { removeAt(index) }
                                    if (selectedFiles.isNotEmpty()) {
                                        viewModel.recognizeImages(selectedFiles)
                                    } else {
                                        viewModel.clearRecognitionState()
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove image",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title *") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text("Author *") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it },
                label = { Text("Barcode") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = releaseDate,
                onValueChange = { releaseDate = it },
                label = { Text("Release Date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        title = ""
                        author = ""
                        barcode = ""
                        releaseDate = ""
                        description = ""
                        selectedFiles = emptyList()
                        selectedModels = emptyList()
                        viewModel.clearRecognitionState()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear")
                }

                Button(
                    onClick = {
                        if (title.isNotBlank() && author.isNotBlank()) {
                            viewModel.addBook(
                                title = title,
                                author = author,
                                barcode = barcode.takeIf { it.isNotBlank() },
                                releaseDate = releaseDate,
                                description = description,
                                imageFiles = selectedFiles
                            ) {
                                onBookAdded()
                            }
                        }
                    },
                    modifier = Modifier.weight(2f),
                    enabled = title.isNotBlank() && author.isNotBlank()
                ) {
                    Text("Save Book")
                }
            }
        }
    }
}
