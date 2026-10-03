import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

export default function BookFormPage() {
	const navigate = useNavigate();

	const [form, setForm] = useState({
		title: '',
		author: '',
		release_date: '',
		description: '',
	});

	const [files, setFiles] = useState([]);
	const [loadingRecognize, setLoadingRecognize] = useState(false);

	const [submitting, setSubmitting] = useState(false);
	const [error, setError] = useState(null);

	const handleChange = (e) => {
		const { name, value } = e.currentTarget;

		setForm((current) => ({
			...current,
			[name]: value,
		}));
	};

	async function handleSubmit(e) {
		e.preventDefault();
		setSubmitting(true);
		setError(null);

		try {

			const formData = new FormData();
			formData.append('book', JSON.stringify(form));
			for (const file of files) {
				formData.append('images', file);
			}
			const response = await fetch('/api/v1/books', {
				method: 'POST',
				body: formData,
			});

			if (!response.ok) {
				throw new Error('Failed to create book');
			} 

			const data = await response.json();
			navigate(`/books/${data.book.id}`);
		} catch (err) {
			setError(err.message);
		} finally {
			setSubmitting(false);
		}
	}



	useEffect(() => {
		if (!files.length) return

		async function recognizeImage(file) {
			setLoadingRecognize(true);
			setError(null);

			const formData = new FormData();
			formData.append('image', file);

			try {
				const response = await fetch('/api/v1/books/recognize', {
					method: 'POST',
					body: formData,
				});
				if (!response.ok) {
					throw new Error('Failed to recognize image');
				}

				const data = await response.json();
				console.log(data)
				const best = data.books?.[0] || null
				if (best) {
					setForm((current) => ({
						...current,
						title: best.title || current.title,
						author: best.author || current.author,
						release_date: best.release_date || current.release_date,
						description: best.description || current.description,
					}))
				} else {
					throw new Error('No book recognized from the image');
				}
			} catch (err) {
				setError(err.message);
			} finally {
				setLoadingRecognize(false);
			}
		}

		recognizeImage(files[0])
	}, [files]);

	return (
		<form onSubmit={handleSubmit} className="book-form">
			<h1>Add Book</h1>
			{error && <p className="error">{error}</p>}

			<label>
				<span>Title</span>
				<input
					type="text"
					name="title"
					value={form.title}
					onChange={handleChange}
					required
				/>
			</label>

			<label>
				<span>Author</span>
				<input
					type="text"
					name="author"
					value={form.author}
					onChange={handleChange}
					required
				/>
			</label>

			<label>
				<span>Release Date</span>
				<input
					type="text"
					name="release_date"
					value={form.release_date}
					onChange={handleChange}
				/>
			</label>

			<label>
				<span>Description</span>
				<textarea
					name="description"
					value={form.description}
					onChange={handleChange}
				></textarea>
			</label>

			<label>
				<span>Images</span>
				<input
					type="file"
					name="files"
					accept="image/*"
					multiple
					onChange={(e) => setFiles(Array.from(e.currentTarget.files))}
				/>
			</label>
			{loadingRecognize && <p>{loadingRecognize ? 'Recognizing image...' : null}</p>}

			<button type="submit" disabled={submitting}>
				{submitting ? 'Submitting...' : 'Add Book'}
			</button>
		</form>
	)
}
