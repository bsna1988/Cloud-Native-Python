import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';

export const loadBooks = createAsyncThunk(
    'books/loadBooks',
    async () => {
        const response = await fetch('/api/v1/books');
        const data = await response.json();

        if (!response.ok) {
            return new Error(data.error || 'Failed to load books');
        }

        return data.books;
    },
    {
        condition: (_, { getState }) => {
            return getState().books.status !== 'loading';
        },
    },
);

const booksSlice = createSlice({
    name: 'books',
    initialState: {
        items: [],
        status: 'idle',
        error: null,
    },
    reducers: {},
    extraReducers: (builder) => {
        builder
            .addCase(loadBooks.pending, (state) => {
                state.status = 'loading';
                state.error = null;
            })
            .addCase(loadBooks.fulfilled, (state, action) => {
                state.status = 'succeeded';
                state.items = action.payload;
            })
            .addCase(loadBooks.rejected, (state, action) => {
                // The StrictMode duplicate is intentionally ignored.
                if (action.payload === 'Books are already loaded or loading.') return;

                state.status = 'failed';
                state.error = action.payload || action.error.message;
            });
    },
});

export default booksSlice.reducer;