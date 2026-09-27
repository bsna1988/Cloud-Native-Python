import BookCard from './components/BookCard';
import './App.css';
import { useState } from 'react';

const books = [
  {
    title: 'The Great Gatsby',
    author_name: 'F. Scott Fitzgerald',
    release_date: '1925'
  },
  {
    title: 'To Kill a Mockingbird',
    author_name: 'Harper Lee',
    release_date: '1960'
  }
];

function App() {
  const [query, setQuery] = useState('');
  const normalizedQuery = query.trim().toLowerCase();

  const visibleBooks = books.filter((book) => {
    const serchableText = `${book.title} ${book.author_name}`.toLowerCase();
    return serchableText.includes(normalizedQuery);
  });

  return (
    <main>
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
      <section className="book-list">
        {visibleBooks.length > 0 ? (
          visibleBooks.map((book) => (
            <BookCard key={book.title} book={book} />
          ))
        ) : (
          <p>No books found.</p>
        )}
      </section>
    </main>
  );
}

export default App;
