package com.homebooks.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.homebooks.android.ui.screens.AddBookScreen
import com.homebooks.android.ui.screens.BookDetailScreen
import com.homebooks.android.ui.screens.BookListScreen
import com.homebooks.android.ui.screens.SplashScreen
import com.homebooks.android.ui.theme.HomeBooksTheme
import com.homebooks.android.ui.viewmodel.BooksViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: BooksViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeBooksTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "splash") {
                        composable("splash") {
                            SplashScreen(
                                onSplashFinished = {
                                    navController.navigate("book_list") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("book_list") {
                            BookListScreen(
                                viewModel = viewModel,
                                onBookClick = { bookId ->
                                    navController.navigate("book_detail/$bookId")
                                },
                                onAddClick = {
                                    navController.navigate("add_book")
                                }
                            )
                        }
                        composable(
                            route = "book_detail/{bookId}",
                            arguments = listOf(navArgument("bookId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val bookId = backStackEntry.arguments?.getInt("bookId") ?: 0
                            BookDetailScreen(
                                bookId = bookId,
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable("add_book") {
                            AddBookScreen(
                                viewModel = viewModel,
                                onBookAdded = { navController.popBackStack() },
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
