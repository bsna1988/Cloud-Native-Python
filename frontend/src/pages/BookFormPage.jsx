import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export default function BookFormPage() {
	const navigate = useNavigate();

	const [form, setForm] = useState({
		title: '',
		author: '',
		release_date: '',
		description: '',
	});

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
			const response = await fetch('/api/v1/books', {
				method: 'POST',
				headers: {
					'Content-Type': 'application/json',
				},
				body: JSON.stringify(form),
			});

			if (!response.ok) {
				throw new Error('Failed to create book');
			}

			const data = await response.json();
			navigate(`/books/${data.data.id}`);
		} catch (err) {
			setError(err.message);
		} finally {
			setSubmitting(false);
		}
	}

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
					type="date"
					name="release_date"
					value={form.release_date}
					onChange={handleChange}
					required
				/>
			</label>

			<label>
				<span>Description</span>
				<textarea
					name="description"
					value={form.description}
					onChange={handleChange}
					required
				></textarea>
			</label>

			<button type="submit" disabled={submitting}>
				{submitting ? 'Submitting...' : 'Add Book'}
			</button>
		</form>
	)
}
