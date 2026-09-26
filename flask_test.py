from app import app
import unittest

class FlaskappTests(unittest.TestCase):

    def setUp(self):
        self.app = app.test_client()
        self.app.testing = True

    def test_home_index(self):
        response = self.app.get('/api/v1/info')
        self.assertEqual(response.status_code, 200)
        self.assertIn(b'api_version', response.data)

    def test_get_books(self):
        response = self.app.get('/api/v1/books')
        self.assertEqual(response.status_code, 200)
        self.assertIn(b'books', response.data)

    def test_get_book(self):
        response = self.app.get('/api/v1/books/1')  # Assuming book with ID 1 exists
        if response.status_code == 200:
            self.assertIn(b'book', response.data)
        else:
            self.assertEqual(response.status_code, 404)  # Book not found case
            
    def test_recognize_book(self):
        # This test assumes you have a valid image file named 'test_image.jpg' in the same directory
        with open('test_data/happiness.png', 'rb') as img:
            response = self.app.post('/api/v1/books/recognize', data={'image': img})
            self.assertEqual(response.status_code, 200)
            self.assertIn(b'title', response.data)
            self.assertIn(b'author', response.data)
            
    def test_delete_book(self):
        response = self.app.delete('/api/v1/books/', data = {'title': 'Test Book'})  # Assuming book with ID 1 exists
        if response.status_code == 200:
            self.assertIn(b'message', response.data)
        else:
            self.assertEqual(response.status_code, 404)  # Book not found case
    
    def test_create_book(self):
        new_book_data = {
            'barcode': '1234567890123',
            'title': 'Test Book',
            'author': 'Test Author',
            'release_date': '2024-01-01',
            'price': 19.99,
            'publisher': 'Test Publisher',
            'description': 'This is a test book.',
            'thumbnail': 'http://example.com/thumbnail.jpg'
        }
        response = self.app.post('/api/v1/books', json=new_book_data)
        if response.status_code == 201:
            self.assertIn(b'book', response.data)
        else:   
            self.assertEqual(response.status_code, 409)
    
    def test_update_book(self):
        updated_book_data = {
            'barcode': '1234567890123',
            'title': 'Updated Test Book',
            'author': 'Updated Test Author',
            'release_date': '2024-01-02',
            'price': 29.99,
            'publisher': 'Updated Test Publisher',
            'description': 'This is an updated test book.',
            'thumbnail': 'http://example.com/updated_thumbnail.jpg'
        }
        response = self.app.put('/api/v1/books/1', json=updated_book_data)  # Assuming book with ID 1 exists
        if response.status_code == 200:
            self.assertIn(b'book', response.data)
        else:
            self.assertEqual(response.status_code, 404)  # Book not found case