import { Link } from "react-router-dom";

function BookCard({ book }) {
    return (
        <article className="book-card">
            <img
                className="book-card__image"
                src={book.thumbnail || '/uploads/no_image.svg'}
                alt={book.thumbnail ? `${book.title} cover` : 'No book cover available'}
            />
            <div className="book-details">
                <h2>
                    <Link to={`/books/${book.id}`}>{book.title}</Link>
                </h2>
                <p>by {book.author}</p>
                <small>{book.release_date}</small>
            </div>
        </article>
    );
}

export default BookCard;