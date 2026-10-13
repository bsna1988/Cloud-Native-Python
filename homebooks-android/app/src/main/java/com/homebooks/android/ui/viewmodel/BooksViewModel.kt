package com.homebooks.android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.homebooks.android.data.model.Book
import com.homebooks.android.data.model.RecognizedBook
import com.homebooks.android.data.repository.BooksRepository
import kotlinx.coroutines.launch
import java.io.File

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

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    private val _recognitionState = MutableLiveData<UiState>()
    val recognitionState: LiveData<UiState> get() = _recognitionState

    fun loadBooks() {
        _booksState.value = UiState.Loading
        viewModelScope.launch {
            val result = _repository.getBooks()
            result.onSuccess { books ->
                _booksState.value = UiState.Success(books)
            }.onFailure { exception ->
                _booksState.value = UiState.Error(exception.message ?: "Unknown error")
            }
        }
    }

    fun loadBook(id: Int) {
        viewModelScope.launch {
            val result = _repository.getBook(id)
            result.onSuccess { book ->
                _selectedBook.value = book
            }.onFailure { exception ->
                _error.value = exception.message ?: "Failed to load book"
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
            result.onSuccess { book ->
                loadBooks()
                onSuccess()
            }.onFailure { exception ->
                _error.value = exception.message ?: "Failed to add book"
            }
        }
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

    fun clearError() {
        _error.value = null
    }

    fun clearRecognitionState() {
        _recognitionState.value = UiState.Idle
    }

    fun deleteBook(id: Int) {
        viewModelScope.launch {
            val result = _repository.deleteBook(id)
            result.onSuccess {
                loadBooks()
            }.onFailure { exception ->
                _error.value = exception.message ?: "Failed to delete book"
            }
        }
    }
}
