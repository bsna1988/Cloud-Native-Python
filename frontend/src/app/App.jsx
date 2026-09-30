import { BrowserRouter, Link, Route, Routes } from 'react-router-dom';
import BookListPage from '../pages/BookListPage';
import BookFormPage from '../pages/BookFormPage';
import BookDetailsPage from '../pages/BookDetailsPage';
import '../styles/App.css';

function App() {

  return (
    <BrowserRouter>
      <header className="app-header">
        <nav className="main-nav">
          <Link to="/">Home</Link>
          <Link to="/books">Books</Link>
          <Link to="/books/new">Add Book</Link>
        </nav>
      </header>
      <main className="app-shell">
        <Routes>
          <Route path="/" element={<BookListPage />} />
          <Route path="/books" element={<BookListPage />} />
          <Route path="/books/new" element={<BookFormPage />} />
          <Route path="/books/:id" element={<BookDetailsPage />} />
        </Routes>
      </main>
    </BrowserRouter>
  );
}

export default App;
