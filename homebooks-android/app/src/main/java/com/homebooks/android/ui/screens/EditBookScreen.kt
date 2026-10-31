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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.homebooks.android.R
import com.homebooks.android.data.model.BookMetadataCandidate
import com.homebooks.android.util.LocalOcrTextCorrector
import com.homebooks.android.ui.viewmodel.BooksViewModel
import com.homebooks.android.ui.viewmodel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBookScreen(
    bookId: Int,
    viewModel: BooksViewModel,
    onBookUpdated: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val book by viewModel.selectedBook.observeAsState(null)

    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var releaseDate by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    var existingImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var removedImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var newFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var newModels by remember { mutableStateOf<List<Any>>(emptyList()) }

    var cameraFile by remember { mutableStateOf<File?>(null) }
    val errorMessage by viewModel.error.observeAsState(null)
    val metadataCandidatesState by viewModel.metadataCandidatesState.observeAsState(UiState.Idle)
    val snackbarHostState = remember { SnackbarHostState() }
    val screenScope = rememberCoroutineScope()
    var isCorrectingText by remember { mutableStateOf(false) }
    var selectedCandidateThumbnail by remember(bookId) { mutableStateOf<String?>(null) }

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    LaunchedEffect(book) {
        book?.let { b ->
            title = b.title
            author = b.author
            barcode = b.barcode ?: ""
            releaseDate = b.release_date
            description = b.description
            existingImages = b.images
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val files = uris.mapNotNull { uri ->
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val imagesDir = File(context.filesDir, "book_images").apply { mkdirs() }
                    val file = File(imagesDir, "book_img_${System.currentTimeMillis()}_${uri.lastPathSegment ?: "image"}.jpg")
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
            if (files.isNotEmpty()) {
                newFiles = newFiles + files
                newModels = newModels + uris
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraFile != null) {
            val file = cameraFile!!
            newFiles = newFiles + file
            newModels = newModels + file
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Edit Book") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
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
        if (book == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (existingImages.isNotEmpty()) {
                    Text("Existing Images:", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        existingImages.forEachIndexed { index, imageUrl ->
                            Box(modifier = Modifier.size(90.dp)) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = "Existing Image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = {
                                        removedImages = removedImages + imageUrl
                                        existingImages = existingImages.toMutableList().apply { removeAt(index) }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove existing image",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

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
                        Text("Add Gallery")
                    }

                    OutlinedButton(
                        onClick = {
                            val imagesDir = File(context.filesDir, "book_images").apply { mkdirs() }
                        val file = File(imagesDir, "book_cam_${System.currentTimeMillis()}.jpg")
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
                        Text("Add Camera")
                    }
                }

                if (newModels.isNotEmpty()) {
                    Text("New Images Added (${newModels.size}):", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        newModels.forEachIndexed { index, model ->
                            Box(modifier = Modifier.size(90.dp)) {
                                AsyncImage(
                                    model = model,
                                    contentDescription = "New Image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = {
                                        newFiles = newFiles.toMutableList().apply { removeAt(index) }
                                        newModels = newModels.toMutableList().apply { removeAt(index) }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove new image",
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

                OutlinedButton(
                    onClick = {
                        val currentBook = book ?: return@OutlinedButton
                        viewModel.searchMetadataCandidates(
                            currentBook.copy(
                                title = title,
                                author = author,
                                barcode = barcode.takeIf(String::isNotBlank)
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = metadataCandidatesState !is UiState.Loading &&
                        !isCorrectingText &&
                        (title.isNotBlank() || author.isNotBlank() || barcode.isNotBlank())
                ) {
                    if (metadataCandidatesState is UiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Searching catalogs…")
                    } else {
                        Text("Search book catalogs")
                    }
                }

                when (val state = metadataCandidatesState) {
                    is UiState.Error -> {
                        Text(
                            text = "Catalog lookup failed: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is UiState.Success<*> -> {
                        val candidates = (state.data as? List<*>)
                            ?.filterIsInstance<BookMetadataCandidate>()
                            .orEmpty()
                        if (candidates.isEmpty()) {
                            Text(
                                "No catalog matches found.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                "Choose a match to fill the form. Review the details before saving.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            candidates.forEach { candidate ->
                                MetadataCandidateCard(candidate) {
                                    title = candidate.title
                                    candidate.author?.takeIf(String::isNotBlank)?.let { author = it }
                                    candidate.barcode?.takeIf(String::isNotBlank)?.let { barcode = it }
                                    candidate.releaseDate?.takeIf(String::isNotBlank)?.let { releaseDate = it }
                                    candidate.description?.takeIf(String::isNotBlank)?.let { description = it }
                                    selectedCandidateThumbnail = candidate.thumbnail
                                    viewModel.clearMetadataCandidates()
                                    screenScope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Candidate details applied. Save changes to update the book."
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> Unit
                }

                OutlinedButton(
                    onClick = {
                        screenScope.launch {
                            isCorrectingText = true
                            try {
                                val currentTitle = title
                                val currentAuthor = author
                                val currentDescription = description
                                val correctedFields = withContext(Dispatchers.Default) {
                                    Triple(
                                        LocalOcrTextCorrector.correctAggressively(currentTitle),
                                        LocalOcrTextCorrector.correctAggressively(currentAuthor),
                                        LocalOcrTextCorrector.correctAggressively(currentDescription)
                                    )
                                }
                                val changedFieldCount = listOf(
                                    currentTitle != correctedFields.first,
                                    currentAuthor != correctedFields.second,
                                    currentDescription != correctedFields.third
                                ).count { it }
                                title = correctedFields.first
                                author = correctedFields.second
                                description = correctedFields.third
                                val message = if (changedFieldCount == 0) {
                                    "No spelling corrections found."
                                } else {
                                    val fieldLabel = if (changedFieldCount == 1) "field" else "fields"
                                    "Corrected $changedFieldCount text $fieldLabel. Review before saving."
                                }
                                snackbarHostState.showSnackbar(message)
                            } finally {
                                isCorrectingText = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCorrectingText &&
                        (title.isNotBlank() || author.isNotBlank() || description.isNotBlank())
                ) {
                    if (isCorrectingText) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Correcting text…")
                    } else {
                        Text("Aggressively fix spelling mistakes")
                    }
                }
                Text(
                    "Applies local corrections to title, author, and description. Review them before saving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        if (title.isNotBlank() && author.isNotBlank()) {
                            viewModel.updateBook(
                                id = bookId,
                                title = title,
                                author = author,
                                barcode = barcode.takeIf { it.isNotBlank() },
                                releaseDate = releaseDate,
                                description = description,
                                imageFiles = newFiles,
                                removedImages = removedImages,
                                remoteImageUrls = listOfNotNull(selectedCandidateThumbnail)
                            ) {
                                onBookUpdated()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCorrectingText && title.isNotBlank() && author.isNotBlank()
                ) {
                    Text("Save Changes")
                }
            }
        }

    }
}

@Composable
private fun MetadataCandidateCard(
    candidate: BookMetadataCandidate,
    onUseCandidate: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = candidate.thumbnail,
                contentDescription = "Cover of ${candidate.title}",
                placeholder = painterResource(id = R.drawable.ic_book_placeholder),
                error = painterResource(id = R.drawable.ic_book_placeholder),
                fallback = painterResource(id = R.drawable.ic_book_placeholder),
                modifier = Modifier
                    .width(72.dp)
                    .height(104.dp)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(candidate.title, style = MaterialTheme.typography.titleMedium)
                candidate.author?.takeIf(String::isNotBlank)?.let {
                    Text("Author: $it", style = MaterialTheme.typography.bodyMedium)
                }
                candidate.releaseDate?.takeIf(String::isNotBlank)?.let {
                    Text("Published: $it", style = MaterialTheme.typography.bodySmall)
                }
                candidate.barcode?.takeIf(String::isNotBlank)?.let {
                    Text("ISBN: $it", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = candidate.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onUseCandidate) {
                    Text("Use this match")
                }
            }
        }
    }
}
