# Notes & Open Questions

## Architectural & Design Decisions
1. **Separation of Image Addition & Recognition:**
   - Image selection and camera photo capture now only add images to the local staging list without triggering automatic OCR/recognition.
   - Users can explicitly trigger recognition by tapping the new **"Recognize Added Images"** button. This saves server compute costs and avoids lag during image selection.
2. **On-Device Barcode Scanning:**
   - Integrated **ZXing Android Embedded** to perform barcode scanning entirely on-device via the phone camera.
   - Once scanned, the ISBN/barcode is sent to `GET /api/v1/books/barcode/<isbn>` to prefill book metadata.
3. **Image Removal (Long-Press & 'X' Button):**
   - Users can remove selected images either by tapping the close ('X') button on the thumbnail or by **long-pressing** the image thumbnail.
4. **Backend Metadata Providers & Barcode Endpoint:**
   - Added `GET /api/v1/books/barcode/<isbn>` backend route.
   - Refactored book metadata searching into extensible providers (Google Books API primary, OpenLibrary fallback).

## Open Questions / Future Improvements
1. Should scanned barcodes also automatically trigger a search if no images are attached? (Currently supported via the dedicated Scan Barcode button).
2. Should offline caching be implemented using Room database for offline book management?
