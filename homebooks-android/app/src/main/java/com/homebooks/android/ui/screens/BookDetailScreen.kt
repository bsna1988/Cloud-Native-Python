package com.homebooks.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: Int,
    viewModel: BooksViewModel,
    onBackClick: () -> Unit
) {
    val book by viewModel.selectedBook.observeAsState(null)

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AsyncImage(
                    model = currentBook.thumbnail,
                    contentDescription = currentBook.title,
                    placeholder = painterResource(id = R.drawable.ic_book_placeholder),
                    error = painterResource(id = R.drawable.ic_book_placeholder),
                    fallback = painterResource(id = R.drawable.ic_book_placeholder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    contentScale = ContentScale.Fit
                )

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
                        currentBook.images.forEachIndexed { index, imageUrl ->
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Book Image ${index + 1}",
                                modifier = Modifier
                                    .size(100.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }
}
