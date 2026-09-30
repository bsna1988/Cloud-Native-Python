import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'

export default function BookDetailsPage() {
    const { id } = useParams()
    const [book, setBook] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    useEffect(() => {
        async function loadBook() {
            try {
                const response = await fetch(`/api/v1/books/${id}`)

                if (!response.ok) {
                    throw new Error('Book not found')
                }

                const data = await response.json()
                setBook(data.book)
            } catch (err) {
                setError(err.message)
            } finally {
                setLoading(false)
            }
        }

        loadBook()
    }, [id])

    if (loading) return <p>Loading book...</p>
    if (error) return <p>Error: {error}</p>
    if (!book) return <p>No book data.</p>

    return (
        <article className="book-detail">
            <Link to="/books">← Back to books</Link>

            <h1>{book.title}</h1>

            <div className="book-meta">
                <p>
                    <strong>Author:</strong> {book.author}
                </p>
                <p>
                    <strong>Release date:</strong> {book.release_date}
                </p>
                <p>
                    <strong>Publisher:</strong> {book.publisher || 'Unknown'}
                </p>
            </div>

            <p>{book.description || 'No description available.'}</p>
        </article>
    )
}