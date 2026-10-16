import { configureStore } from '@reduxjs/toolkit';
import booksReducer from '../features/books/booksSLice.js';

export const store = configureStore({
  reducer: {
    books: booksReducer,
  },
});
