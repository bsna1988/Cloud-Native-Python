package com.homebooks.android.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.MultiFormatReader
import com.google.zxing.DecodeHintType
import com.homebooks.android.data.model.RecognizedBook
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.EnumMap

object OnDeviceRecognitionEngine {

    fun recognizeImagesLocally(context: Context, imageFiles: List<File>): Result<List<RecognizedBook>> {
        if (imageFiles.isEmpty()) {
            return Result.failure(Exception("On-device recognition could not be completed: No images provided."))
        }

        val recognizedBooks = mutableListOf<RecognizedBook>()
        val readableFiles = imageFiles.filter { it.isFile && it.canRead() }
        if (readableFiles.isEmpty()) {
            return Result.failure(Exception("On-device recognition could not read the selected image."))
        }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            for (file in readableFiles) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    try {
                        val pixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(
                            pixels,
                            0,
                            bitmap.width,
                            0,
                            0,
                            bitmap.width,
                            bitmap.height
                        )
                        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
                        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
                        hints[DecodeHintType.TRY_HARDER] = true

                        try {
                            val barcode = MultiFormatReader().decode(binaryBitmap, hints).text
                            if (barcode.length in 8..14) {
                                recognizedBooks.add(
                                    RecognizedBook(
                                        title = "Book (ISBN: $barcode)",
                                        author = "",
                                        barcode = barcode,
                                        release_date = "",
                                        description = "Barcode recognized on-device."
                                    )
                                )
                                continue
                            }
                        } catch (_: Exception) {
                            // Continue with OCR when no barcode is present.
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }

                val image = InputImage.fromFilePath(context, Uri.fromFile(file))
                val result = Tasks.await(recognizer.process(image))
                val candidate = result.textBlocks
                    .map { normalizeOcrText(it.text) }
                    .filter(::isCandidateSpineText)
                    .maxByOrNull(String::length)

                if (candidate != null) {
                    recognizedBooks.add(
                        RecognizedBook(
                            title = candidate,
                            author = "",
                            barcode = null,
                            release_date = "",
                            description = "Text recognized on-device. Verify title and author."
                        )
                    )
                }
            }

            if (recognizedBooks.isEmpty()) {
                Result.failure(
                    Exception(
                        "No readable book text or barcode was found. Try a closer, sharper photo or enter book details manually."
                    )
                )
            } else {
                Result.success(recognizedBooks)
            }
        } catch (exception: Exception) {
            Result.failure(
                Exception(
                    "On-device recognition failed. Please try another image or enter book details manually.",
                    exception
                )
            )
        } finally {
            recognizer.close()
        }
    }

    fun recognizeShelfLocally(context: Context, imageFiles: List<File>): Result<List<RecognizedBook>> {
        if (imageFiles.isEmpty()) {
            return Result.failure(Exception("On-device bookshelf recognition could not be completed: No images provided."))
        }

        val readableFiles = imageFiles.filter { it.isFile && it.canRead() }
        if (readableFiles.isEmpty()) {
            return Result.failure(Exception("On-device bookshelf recognition could not read the selected image."))
        }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val candidatesByText = linkedMapOf<String, RecognizedBook>()
            for (file in readableFiles) {
                val image = InputImage.fromFilePath(context, Uri.fromFile(file))
                addOcrCandidates(
                    candidatesByText,
                    Tasks.await(recognizer.process(image)).textBlocks.map { it.text }
                )

                if (candidatesByText.size >= MAX_SHELF_CANDIDATES) break
            }

            if (candidatesByText.isEmpty()) {
                Result.failure(
                    Exception(
                        "No readable book-spine text was found. Try a closer, sharper photo of one shelf row."
                    )
                )
            } else {
                Result.success(candidatesByText.values.toList())
            }
        } catch (exception: Exception) {
            Result.failure(
                Exception(
                    "On-device bookshelf text recognition failed. Please try another image or enter book details manually.",
                    exception
                )
            )
        } finally {
            recognizer.close()
        }
    }

    private fun normalizeOcrText(text: String): String =
        text.replace(WHITESPACE, " ").trim()

    private fun addOcrCandidates(
        candidates: MutableMap<String, RecognizedBook>,
        textBlocks: List<String>
    ) {
        for (block in textBlocks) {
            val text = normalizeOcrText(block)
            if (!isCandidateSpineText(text)) continue
            val correctedText = LocalOcrTextCorrector.correct(text)

            candidates.putIfAbsent(
                correctedText.lowercase(),
                RecognizedBook(
                    title = correctedText,
                    raw_title = text.takeIf { correctedText != text },
                    author = "",
                    barcode = null,
                    release_date = "",
                    description = "Text recognized on-device. Verify title and author."
                )
            )
            if (candidates.size >= MAX_SHELF_CANDIDATES) return
        }
    }

    private fun isCandidateSpineText(text: String): Boolean {
        if (text.length !in MIN_CANDIDATE_LENGTH..MAX_CANDIDATE_LENGTH) return false
        return text.count(Char::isLetter) >= MIN_LETTER_COUNT
    }

    private const val MAX_SHELF_CANDIDATES = 50
    private const val MIN_CANDIDATE_LENGTH = 3
    private const val MAX_CANDIDATE_LENGTH = 140
    private const val MIN_LETTER_COUNT = 3
    private val WHITESPACE = Regex("\\s+")
}
