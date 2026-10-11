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

    const imageUrls = [...new Set([book.thumbnail, ...(book.images || [])].filter(Boolean))]
    const coverUrl = book.thumbnail || imageUrls[0] || '/uploads/no_image.svg'
    const galleryImageUrls = imageUrls.filter((imageUrl) => imageUrl !== coverUrl)

    return (
        <article className="book-detail">
            <Link to="/books">← Back to books</Link>

            <h1>{book.title}</h1>

            <section className="book-detail__images" aria-label="Book images">
                <img
                    className="book-detail__cover"
                    src={coverUrl}
                    alt={imageUrls.length ? `${book.title} cover` : 'No book cover available'}
                />
                {galleryImageUrls.length > 0 && (
                    <div className="book-detail__gallery">
                        {galleryImageUrls.map((imageUrl, index) => (
                            <img
                                key={imageUrl}
                                src={imageUrl}
                                alt={`${book.title} image ${index + 1}`}
                            />
                        ))}
                    </div>
                )}
            </section>

            <div className="book-meta">
                <p>
                    <strong>Author:</strong> {book.author}
                </p>
                {book.barcode && (
                    <p>
                        <strong>ISBN:</strong> {book.barcode}
                    </p>
                )}
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