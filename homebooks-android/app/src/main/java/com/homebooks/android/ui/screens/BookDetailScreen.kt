package com.homebooks.android.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.homebooks.android.R
import com.homebooks.android.ui.viewmodel.BooksViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: Int,
    viewModel: BooksViewModel,
    onEditClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val book by viewModel.selectedBook.observeAsState(null)
    var fullscreenIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.title ?: "Book Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Book")
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
            val currentBook = book!!
            val allImages = buildList {
                currentBook.thumbnail?.let { add(it) }
                addAll(currentBook.images)
            }.distinct()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (currentBook.thumbnail != null) {
                    val thumbIndex = allImages.indexOf(currentBook.thumbnail)
                    AsyncImage(
                        model = currentBook.thumbnail,
                        contentDescription = currentBook.title,
                        placeholder = painterResource(id = R.drawable.ic_book_placeholder),
                        error = painterResource(id = R.drawable.ic_book_placeholder),
                        fallback = painterResource(id = R.drawable.ic_book_placeholder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clickable {
                                fullscreenIndex = if (thumbIndex >= 0) thumbIndex else 0
                            },
                        contentScale = ContentScale.Fit
                    )
                }

                Text(
                    text = currentBook.title,
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = "Author: ${currentBook.author}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!currentBook.barcode.isNullOrBlank()) {
                    Text(
                        text = "Barcode: ${currentBook.barcode}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (currentBook.release_date.isNotBlank()) {
                    Text(
                        text = "Release Date: ${currentBook.release_date}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (currentBook.description.isNotBlank()) {
                    Divider()
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = currentBook.description,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                if (currentBook.images.isNotEmpty()) {
                    Divider()
                    Text(
                        text = "Additional Images",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        currentBook.images.forEachIndexed { _, imageUrl ->
                            val globalIndex = allImages.indexOf(imageUrl)
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Book Image",
                                modifier = Modifier
                                    .size(100.dp)
                                    .clickable {
                                        fullscreenIndex = if (globalIndex >= 0) globalIndex else 0
                                    },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            fullscreenIndex?.let { index ->
                FullscreenImageViewer(
                    images = allImages,
                    initialIndex = index,
                    onDismiss = { fullscreenIndex = null }
                )
            }
        }
    }
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullscreenImageViewer(
    images: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { images.size }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = images[page],
                        contentDescription = "Full screen image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
    }
}
