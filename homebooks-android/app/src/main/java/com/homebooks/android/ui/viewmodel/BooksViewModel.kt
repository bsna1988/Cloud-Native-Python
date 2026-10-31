package com.homebooks.android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.homebooks.android.data.model.Book
import com.homebooks.android.data.model.BookMetadataCandidate
import com.homebooks.android.data.model.RecognizedBook
import com.homebooks.android.data.repository.BooksRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.io.File
import java.util.UUID

enum class ScanStatus {
    LOOKING_UP,
    ADDED,
    ALREADY_EXISTS,
    NOT_FOUND,
    NETWORK_ERROR,
    INVALID
}

data class ScanResultItem(
    val id: String = UUID.randomUUID().toString(),
    val isbn: String,
    val title: String? = null,
    val status: ScanStatus,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success<T>(val data: T) : UiState()
    data class Error(val message: String) : UiState()
}

class BooksViewModel(application: Application) : AndroidViewModel(application) {

    private val _repository: BooksRepository =
        BooksRepository(
            apiService = com.homebooks.android.data.api.RetrofitClient.createService(),
            context = application
        )

    private val _booksState = MutableLiveData<UiState>()
    val booksState: LiveData<UiState> get() = _booksState

    private val _selectedBook = MutableLiveData<Book?>()
    val selectedBook: LiveData<Book?> get() = _selectedBook

    private val _metadataCandidatesState = MutableLiveData<UiState>(UiState.Idle)
    val metadataCandidatesState: LiveData<UiState> get() = _metadataCandidatesState
    private var metadataLookupJob: Job? = null

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    private val _bookDeletedMessage = MutableLiveData<String?>()
    val bookDeletedMessage: LiveData<String?> get() = _bookDeletedMessage

    private val _recognitionState = MutableLiveData<UiState>()
    val recognitionState: LiveData<UiState> get() = _recognitionState

    private val _bookshelfRecognitionState = MutableLiveData<UiState>()
    val bookshelfRecognitionState: LiveData<UiState> get() = _bookshelfRecognitionState

    val pendingCount: LiveData<Int> = _repository.observePendingMutationsCount().asLiveData()

    private val _continuousScanResults = MutableLiveData<List<ScanResultItem>>(emptyList())
    val continuousScanResults: LiveData<List<ScanResultItem>> get() = _continuousScanResults

    private val _activeScanStatus = MutableLiveData<ScanResultItem?>()
    val activeScanStatus: LiveData<ScanResultItem?> get() = _activeScanStatus

    private val processedSessionIsbns = mutableSetOf<String>()
    private val scanQueue = Channel<String>(Channel.UNLIMITED)

    init {
        observeBooks()
        loadBooks()
        viewModelScope.launch {
            for (isbn in scanQueue) {
                processScanItem(isbn)
            }
        }
    }

    private fun normalizeAndValidateIsbn(raw: String): String? {
        if (raw.isBlank()) return null
        val cleaned = raw.replace(Regex("[^0-9Xx]"), "").uppercase()
        if (cleaned.length in 8..14) {
            return cleaned
        }
        return null
    }

    fun enqueueScan(rawIsbn: String) {
        val normalized = normalizeAndValidateIsbn(rawIsbn)
        if (normalized == null) {
            val item = ScanResultItem(isbn = rawIsbn, status = ScanStatus.INVALID)
            emitScanResult(item)
            return
        }
        if (processedSessionIsbns.contains(normalized)) {
            return
        }
        processedSessionIsbns.add(normalized)

        viewModelScope.launch {
            scanQueue.send(normalized)
        }
    }

    private fun emitScanResult(item: ScanResultItem) {
        _activeScanStatus.postValue(item)
        val currentList = _continuousScanResults.value.orEmpty()
        _continuousScanResults.postValue(listOf(item) + currentList.take(49))
    }

    private suspend fun processScanItem(isbn: String) {
        val lookingUpItem = ScanResultItem(isbn = isbn, status = ScanStatus.LOOKING_UP)
        emitScanResult(lookingUpItem)

        try {
            val localBooks = _repository.getBooks().getOrNull() ?: emptyList()
            val existsByBarcode = localBooks.any { it.barcode == isbn }
            if (existsByBarcode) {
                emitScanResult(ScanResultItem(isbn = isbn, status = ScanStatus.ALREADY_EXISTS))
                return
            }

            val searchResult = _repository.searchByBarcode(isbn)
            searchResult.onFailure {
                emitScanResult(ScanResultItem(isbn = isbn, status = ScanStatus.NETWORK_ERROR))
            }.onSuccess { recognizedBooks ->
                val best = recognizedBooks.firstOrNull()
                if (best == null || best.title.isNullOrBlank()) {
                    emitScanResult(ScanResultItem(isbn = isbn, status = ScanStatus.NOT_FOUND))
                } else {
                    val existsByTitle = localBooks.any { it.title.equals(best.title, ignoreCase = true) }
                    if (existsByTitle) {
                        emitScanResult(ScanResultItem(isbn = isbn, title = best.title, status = ScanStatus.ALREADY_EXISTS))
                    } else {
                        val addResult = _repository.addBook(
                            title = best.title ?: "Untitled",
                            author = best.author ?: "Unknown Author",
                            barcode = isbn,
                            releaseDate = best.release_date ?: "",
                            description = best.description ?: "",
                            imageFiles = emptyList()
                        )
                        addResult.onSuccess {
                            emitScanResult(ScanResultItem(isbn = isbn, title = best.title, status = ScanStatus.ADDED))
                        }.onFailure {
                            emitScanResult(ScanResultItem(isbn = isbn, title = best.title, status = ScanStatus.NETWORK_ERROR))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emitScanResult(ScanResultItem(isbn = isbn, status = ScanStatus.NETWORK_ERROR))
        }
    }

    private fun observeBooks() {
        viewModelScope.launch {
            _repository.observeBooks().collectLatest { books ->
                _booksState.value = UiState.Success(books)
            }
        }
    }

    fun loadBooks() {
        viewModelScope.launch {
            _repository.getBooks()
            _repository.triggerImmediateSync()
        }
    }

    fun loadBook(id: Int) {
        metadataLookupJob?.cancel()
        metadataLookupJob = null
        _metadataCandidatesState.value = UiState.Idle
        viewModelScope.launch {
            val result = _repository.getBook(id)
            result.onSuccess { book ->
                _selectedBook.value = book
            }.onFailure { exception ->
                _error.value = exception.message ?: "Failed to load book"
            }
        }
    }

    fun searchMetadataCandidates(book: Book) {
        if (book.title.isBlank() && book.author.isBlank() && book.barcode.isNullOrBlank()) {
            _metadataCandidatesState.value = UiState.Error(
                "Enter a title, author, or barcode before searching."
            )
            return
        }

        metadataLookupJob?.cancel()
        _metadataCandidatesState.value = UiState.Loading
        metadataLookupJob = viewModelScope.launch {
            _repository.findMetadataCandidates(book)
                .onSuccess { candidates ->
                    _metadataCandidatesState.value = UiState.Success(candidates)
                }
                .onFailure { exception ->
                    _metadataCandidatesState.value = UiState.Error(
                        exception.message ?: "Metadata lookup failed"
                    )
                }
            metadataLookupJob = null
        }
    }

    fun clearMetadataCandidates() {
        metadataLookupJob?.cancel()
        metadataLookupJob = null
        _metadataCandidatesState.value = UiState.Idle
    }

    fun acceptMetadataCandidate(book: Book, candidate: BookMetadataCandidate) {
        _metadataCandidatesState.value = UiState.Loading
        viewModelScope.launch {
            _repository.acceptMetadataCandidate(book, candidate)
                .onSuccess { updatedBook ->
                    _selectedBook.value = updatedBook
                    _metadataCandidatesState.value = UiState.Idle
                }
                .onFailure { exception ->
                    val message = exception.message ?: "Failed to apply metadata"
                    _metadataCandidatesState.value = UiState.Error(message)
                    _error.value = message
                }
        }
    }

    fun addBook(
        title: String,
        author: String,
        barcode: String?,
        releaseDate: String,
        description: String,
        imageFiles: List<File>,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = _repository.addBook(title, author, barcode, releaseDate, description, imageFiles)
            result.onSuccess {
                onSuccess()
            }.onFailure { exception ->
                _error.value = exception.message ?: "Failed to add book"
            }
        }
    }

    suspend fun addBookSync(
        title: String,
        author: String,
        barcode: String?,
        releaseDate: String,
        description: String,
        imageFiles: List<File>
    ): Result<Book> {
        return _repository.addBook(title, author, barcode, releaseDate, description, imageFiles)
    }

    fun recognizeImages(imageFiles: List<File>) {
        _recognitionState.value = UiState.Loading
        viewModelScope.launch {
            val result = _repository.recognizeImages(imageFiles)
            result.onSuccess { books ->
                _recognitionState.value = UiState.Success(books)
            }.onFailure { exception ->
                _recognitionState.value = UiState.Error(exception.message ?: "Recognition failed")
            }
        }
    }

    fun recognizeShelf(imageFiles: List<File>) {
        _bookshelfRecognitionState.value = UiState.Loading
        viewModelScope.launch {
            val result = _repository.recognizeShelf(imageFiles)
            result.onSuccess { books ->
                _bookshelfRecognitionState.value = UiState.Success(books)
            }.onFailure { exception ->
                _bookshelfRecognitionState.value = UiState.Error(exception.message ?: "Bookshelf recognition failed")
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearRecognitionState() {
        _recognitionState.value = UiState.Idle
    }

    fun clearBookshelfRecognitionState() {
        _bookshelfRecognitionState.value = UiState.Idle
    }

    fun deleteBook(id: Int, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val result = _repository.deleteBook(id)
            result.onSuccess {
                _bookDeletedMessage.value = "Book deleted"
                onSuccess()
            }
            result.onFailure { exception ->
                _error.value = exception.message ?: "Failed to delete book"
            }
        }
    }

    fun clearBookDeletedMessage() {
        _bookDeletedMessage.value = null
    }

    fun updateBook(
        id: Int,
        title: String,
        author: String,
        barcode: String?,
        releaseDate: String,
        description: String,
        imageFiles: List<File>,
        removedImages: List<String>,
        remoteImageUrls: List<String> = emptyList(),
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val result = _repository.updateBook(
                id,
                title,
                author,
                barcode,
                releaseDate,
                description,
                imageFiles,
                removedImages,
                remoteImageUrls
            )
            result.onSuccess { book ->
                _selectedBook.value = book
                onSuccess()
            }.onFailure { exception ->
                _error.value = exception.message ?: "Failed to update book"
            }
        }
    }

    fun searchByBarcode(isbn: String) {
        _recognitionState.value = UiState.Loading
        viewModelScope.launch {
            val result = _repository.searchByBarcode(isbn)
            result.onSuccess { books ->
                _recognitionState.value = UiState.Success(books)
            }.onFailure { exception ->
                _recognitionState.value = UiState.Error(exception.message ?: "Barcode search failed")
            }
        }
    }

    fun syncNow() {
        _repository.triggerImmediateSync()
        loadBooks()
    }
}
