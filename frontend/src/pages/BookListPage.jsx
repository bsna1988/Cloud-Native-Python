import { useEffect, useState } from "react";
import { useDispatch, useSelector } from 'react-redux';
import { Link } from 'react-router-dom'
import BookCard from "@/components/BookCard";
import { loadBooks } from "@/features/books/booksSlice";

function BookListPage() {
    const dispatch = useDispatch()
    const books = useSelector((state) => state.books.items)
    const status = useSelector((state) => state.books.status)
    const error = useSelector((state) => state.books.error)
    const [query, setQuery] = useState('')

    const normalizedQuery = query.trim().toLowerCase()

    const visibleBooks = books.filter((book) => {
        const searchableText = `${book.author} ${book.title}`.toLowerCase()
        return searchableText.includes(normalizedQuery)
    })

    useEffect(() => {
        dispatch(loadBooks())
    }, [dispatch])


    if (status === 'loading' || status === 'idle') {
        return <p>Loading books...</p>;
    }

    if (status === 'failed') {
        return (
            <>
                <p>Error: {error}</p>
                <button type="button" onClick={() => dispatch(loadBooks())}>
                    Retry
                </button>
            </>
        );
    }


    return (
        <>
            <h1>My Library</h1>
            <label className="book-search">
                <span>Search books</span>
                <input
                    type="search"
                    value={query}
                    onChange={(event) => setQuery(event.currentTarget.value)}
                    placeholder='Title or author'
                />
            </label>
            <div className="page-actions">
                <Link to="/books/new" className="primary-btn">
                    Add Book
                </Link>
            </div>
            <section className="book-list">
                {visibleBooks.length > 0 ? (
                    visibleBooks.map((book) => (
                        <BookCard key={book.title} book={book} />
                    ))
                ) : (
                    <p>No books found.</p>
                )}
            </section>
        </>
    )
}

export default BookListPage
