package com.homebooks.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.homebooks.android.data.model.Book
import com.homebooks.android.data.model.RecognizedBook
import com.homebooks.android.ui.viewmodel.BooksViewModel
import com.homebooks.android.ui.viewmodel.UiState
import kotlinx.coroutines.launch
import java.io.File

enum class ImportStatus {
    IDLE, IMPORTING, SUCCESS, FAILED
}

data class ShelfReviewItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    var title: String,
    val originalTitle: String?,
    var author: String,
    val releaseDate: String,
    val description: String,
    val barcode: String?,
    var isSelected: Boolean,
    val isDuplicate: Boolean,
    var importStatus: ImportStatus = ImportStatus.IDLE,
    var errorMessage: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfReviewScreen(
    viewModel: BooksViewModel,
    onImportComplete: () -> Unit,
    onBackClick: () -> Unit
) {
    val bookshelfRecognitionState by viewModel.bookshelfRecognitionState.observeAsState(UiState.Loading)
    val booksState by viewModel.booksState.observeAsState(UiState.Loading)
    val coroutineScope = rememberCoroutineScope()

    var reviewItems by remember { mutableStateOf<List<ShelfReviewItem>>(emptyList()) }
    var isInitialized by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

    LaunchedEffect(bookshelfRecognitionState, booksState) {
        if (!isInitialized) {
            val recognizedBooks = when (val state = bookshelfRecognitionState) {
                is UiState.Success<*> -> (state.data as? List<*>)?.filterIsInstance<RecognizedBook>() ?: emptyList()
                else -> emptyList()
            }
            val localBooks = when (val state = booksState) {
                is UiState.Success<*> -> (state.data as? List<*>)?.filterIsInstance<Book>() ?: emptyList()
                else -> emptyList()
            }

            if (recognizedBooks.isNotEmpty() || bookshelfRecognitionState is UiState.Success<*>) {
                reviewItems = recognizedBooks.map { book ->
                    val title = book.title ?: ""
                    val author = book.author ?: ""
                    val barcode = book.barcode

                    val isDuplicate = localBooks.any { local ->
                        (title.isNotBlank() && local.title.equals(title, ignoreCase = true)) ||
                            (!barcode.isNullOrBlank() && local.barcode == barcode)
                    }

                    ShelfReviewItem(
                        title = title,
                        originalTitle = book.raw_title,
                        author = author,
                        releaseDate = book.release_date ?: "",
                        description = book.description ?: "",
                        barcode = barcode,
                        isSelected = !isDuplicate,
                        isDuplicate = isDuplicate
                    )
                }
                isInitialized = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Bookshelf Books") },
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
        },
        bottomBar = {
            if (reviewItems.isNotEmpty()) {
                Surface(
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Button(
                            onClick = {
                                isImporting = true
                                coroutineScope.launch {
                                    val itemsToImport = reviewItems.filter { it.isSelected && it.importStatus != ImportStatus.SUCCESS }
                                    for (item in itemsToImport) {
                                        reviewItems = reviewItems.map {
                                            if (it.id == item.id) it.copy(importStatus = ImportStatus.IMPORTING) else it
                                        }

                                        val result = viewModel.addBookSync(
                                            title = item.title,
                                            author = item.author,
                                            barcode = item.barcode,
                                            releaseDate = item.releaseDate,
                                            description = item.description,
                                            imageFiles = emptyList<File>()
                                        )

                                        reviewItems = reviewItems.map {
                                            if (it.id == item.id) {
                                                if (result.isSuccess) {
                                                    it.copy(importStatus = ImportStatus.SUCCESS)
                                                } else {
                                                    it.copy(importStatus = ImportStatus.FAILED, errorMessage = result.exceptionOrNull()?.message)
                                                }
                                            } else {
                                                it
                                            }
                                        }
                                    }
                                    isImporting = false
                                }
                            },
                            enabled = !isImporting && reviewItems.any { it.isSelected && it.importStatus != ImportStatus.SUCCESS },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = if (isImporting) "Importing Books..." else "Import Selected Books (${reviewItems.count { it.isSelected && it.importStatus != ImportStatus.SUCCESS }})")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = bookshelfRecognitionState) {
                is UiState.Loading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Recognizing books from bookshelf photo...")
                    }
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Recognition Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onBackClick) {
                            Text(text = "Go Back")
                        }
                    }
                }
                is UiState.Success<*> -> {
                    if (reviewItems.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No readable books recognized from bookshelf image.",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onBackClick) {
                                Text(text = "Go Back")
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(reviewItems, key = { it.id }) { item ->
                                ShelfReviewCard(
                                    item = item,
                                    onChanged = { updated ->
                                        reviewItems = reviewItems.map { if (it.id == item.id) updated else it }
                                    }
                                )
                            }
                        }
                    }
                }
                else -> {
                    // Idle state, e.g. if navigated without recognition
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "No bookshelf image provided.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onBackClick) {
                            Text(text = "Go Back")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShelfReviewCard(
    item: ShelfReviewItem,
    onChanged: (ShelfReviewItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = item.isSelected,
                    onCheckedChange = { onChanged(item.copy(isSelected = it)) },
                    enabled = item.importStatus != ImportStatus.IMPORTING && item.importStatus != ImportStatus.SUCCESS
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (item.isDuplicate) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Duplicate",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Already in library (Unselected by default)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                when (item.importStatus) {
                    ImportStatus.IMPORTING -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    ImportStatus.SUCCESS -> {
                        Icon(Icons.Default.Check, contentDescription = "Success", tint = Color.Green)
                    }
                    ImportStatus.FAILED -> {
                        Text("Failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    ImportStatus.IDLE -> {}
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = item.title,
                onValueChange = { onChanged(item.copy(title = it)) },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = item.importStatus == ImportStatus.IDLE
            )

            if (!item.originalTitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Original OCR: ${item.originalTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = item.author,
                onValueChange = { onChanged(item.copy(author = it)) },
                label = { Text("Author") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = item.importStatus == ImportStatus.IDLE
            )

            if (item.importStatus == ImportStatus.FAILED && !item.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
