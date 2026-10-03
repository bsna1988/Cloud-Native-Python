import { Link } from "react-router-dom";

function BookCard({ book }) {
    return (
        <article className="book-card">
            {book.thumbnail && (
                <img className="book-card__image" src={book.thumbnail} alt={`${book.title} cover`}/>
            )}
            <div className="book-details">
                <h2>
                    <Link to={`/books/${book.id}`}>{book.title}</Link>
                </h2>
                <p>by {book.author_name}</p>
                <small>{book.release_date}</small>
            </div>
        </article>
    );
}

export default BookCard;