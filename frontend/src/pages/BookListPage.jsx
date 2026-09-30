import { useEffect, useState } from "react";
import { Link } from 'react-router-dom'
import BookCard from "../components/BookCard";

function BookListPage() {
    const [books, setBooks] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [query, setQuery] = useState('')

    const normalizedQuery = query.trim().toLowerCase()

    const visibleBooks = books.filter((book) => {
        const searchableText = `${book.author_name} ${book.title}`.toLowerCase()
        return searchableText.includes(normalizedQuery)
    })

    useEffect(() => {
        async function loadBooks() {
            try {
                const response = await fetch('/api/v1/books')

                if (!response.ok) {
                    throw new Error('Failed to load books')
                }

                const data = await response.json()
                setBooks(data.books)
            } catch (err) {
                setError(err.message)
            } finally {
                setLoading(false)
            }
        }
        loadBooks()
    }, [])

    if (loading) return <p>Loading books...</p>
    if (error) return <p>Error: {error}</p>
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