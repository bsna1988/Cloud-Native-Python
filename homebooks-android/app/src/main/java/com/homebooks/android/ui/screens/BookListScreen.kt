package com.homebooks.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.homebooks.android.R
import com.homebooks.android.data.model.Book
import com.homebooks.android.ui.viewmodel.BooksViewModel
import com.homebooks.android.ui.viewmodel.UiState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    viewModel: BooksViewModel,
    onBookClick: (Int) -> Unit,
    onAddClick: () -> Unit,
    onBulkScanClick: () -> Unit,
    onScanShelfComplete: () -> Unit
) {
    val context = LocalContext.current
    val booksState by viewModel.booksState.observeAsState(UiState.Loading)
    val errorMessage by viewModel.error.observeAsState(null)
    val pendingCount by viewModel.pendingCount.observeAsState(0)
    var searchQuery by remember { mutableStateOf("") }
    var showScanShelfDialog by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraFile != null) {
            val file = cameraFile!!
            viewModel.recognizeShelf(listOf(file))
            onScanShelfComplete()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = File(context.cacheDir, "shelf_${System.currentTimeMillis()}.jpg")
                inputStream?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                viewModel.recognizeShelf(listOf(file))
                onScanShelfComplete()
            } catch (_: Exception) {}
        }
    }

    if (showScanShelfDialog) {
        AlertDialog(
            onDismissRequest = { showScanShelfDialog = false },
            title = { Text("Scan Bookshelf") },
            text = { Text("Choose how to provide the bookshelf image for recognition.") },
            confirmButton = {
                TextButton(onClick = {
                    showScanShelfDialog = false
                    val file = File(context.cacheDir, "shelf_cam_${System.currentTimeMillis()}.jpg")
                    cameraFile = file
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    cameraLauncher.launch(uri)
                }) {
                    Text("Take Photo")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showScanShelfDialog = false
                    galleryLauncher.launch("image/*")
                }) {
                    Text("Choose from Gallery")
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.loadBooks()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "HomeBooks") },
                actions = {
                    TextButton(onClick = { showScanShelfDialog = true }) {
                        Text("Scan Bookshelf", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    TextButton(onClick = onBulkScanClick) {
                        Text("Bulk Scan", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "Add Book")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search books...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true
            )

            if (pendingCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Pending offline changes ($pendingCount)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            viewModel.syncNow()
                        }) {
                            Text("Sync Now")
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when (val state = booksState) {
                    is UiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is UiState.Success<*> -> {
                        val books = (state.data as? List<*>)?.filterIsInstance<Book>() ?: emptyList()
                        val filteredBooks = books.filter { book ->
                            searchQuery.isBlank() ||
                                book.title.contains(searchQuery, ignoreCase = true) ||
                                book.author.contains(searchQuery, ignoreCase = true)
                        }

                        if (filteredBooks.isEmpty()) {
                            Text(
                                text = "No books found.",
                                modifier = Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredBooks, key = { it.id ?: it.hashCode() }) { book ->
                                    val dismissState = rememberDismissState(
                                        confirmValueChange = { dismissValue ->
                                            if (dismissValue == DismissValue.DismissedToStart || dismissValue == DismissValue.DismissedToEnd) {
                                                book.id?.let { viewModel.deleteBook(it) }
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                    )
                                    SwipeToDismiss(
                                        state = dismissState,
                                        background = {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        MaterialTheme.colorScheme.errorContainer,
                                                        shape = MaterialTheme.shapes.medium
                                                    )
                                                    .padding(horizontal = 20.dp),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        },
                                        dismissContent = {
                                            BookItemCard(book = book, onClick = {
                                                book.id?.let { onBookClick(it) }
                                            })
                                        }
                                    )
                                }
                            }
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
                                text = "Error: ${state.message}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.loadBooks() }) {
                                Text(text = "Retry")
                            }
                        }
                    }
                    else -> {}
                }

                errorMessage?.let { msg ->
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.clearError() }) {
                                Text(text = "Dismiss")
                            }
                        }
                    ) {
                        Text(text = msg)
                    }
                }
            }
        }
    }
}

@Composable
fun BookItemCard(
    book: Book,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = book.thumbnail,
                contentDescription = book.title,
                placeholder = painterResource(id = R.drawable.ic_book_placeholder),
                error = painterResource(id = R.drawable.ic_book_placeholder),
                fallback = painterResource(id = R.drawable.ic_book_placeholder),
                modifier = Modifier
                    .size(60.dp)
                    .padding(end = 12.dp),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = book.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = book.author.ifBlank { "Unknown Author" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (book.release_date.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Released: ${book.release_date}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
