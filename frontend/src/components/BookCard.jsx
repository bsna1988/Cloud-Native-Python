function BookCard({ book }) {
    return (
        <article className="book-card">
            {book.thumbnail && (
                <img src={book.thumbnail} alt={`${book.title} cover`}/>
            )}
            <div className="book-details">
                <h2>{book.title}</h2>
                <p>by {book.author_name}</p>
                <small>{book.release_date}</small>
            </div>
        </article>
    );
}

export default BookCard;