package com.example.tutorconnect.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import android.util.Log
import android.widget.ImageView
import com.example.tutorconnect.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream


// Simple in-memory bitmap cache for decoded Base64 images

private val bitmapCache: LruCache<String, Bitmap> by lazy {
    val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    val cacheSize = maxMemory / 8 // use 1/8 of available memory
    object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }
}

// Decode Base64 string safely and efficiently
suspend fun decodeBase64ToBitmap(base64String: String?): Bitmap? = withContext(Dispatchers.IO) {
    try {
        if (base64String.isNullOrBlank()) return@withContext null

        // Check cache first
        bitmapCache.get(base64String)?.let {
            Log.d("ImageUtils", "⚡ Loaded bitmap from cache (len=${base64String.length})")
            return@withContext it
        }

        // Clean and normalize Base64 string
        val cleanBase64 = base64String
            .replace("data:image/jpeg;base64,", "", ignoreCase = true)
            .replace("data:image/png;base64,", "", ignoreCase = true)
            .replace("\\s".toRegex(), "")
            .trim()

        val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)

        // Decode efficiently to prevent OOM
        val options = BitmapFactory.Options().apply { inSampleSize = 2 } // half resolution
        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, options)

        // Cache the decoded bitmap
        bitmap?.let { bitmapCache.put(base64String, it) }

        bitmap
    } catch (e: Exception) {
        Log.e("ImageUtils", "❌ Exception while decoding Base64: ${e.message}", e)
        null
    }
}


// Load image into an ImageView with fade-in
suspend fun ImageView.loadProfileImage(base64String: String?) {
    val bitmap = decodeBase64ToBitmap(base64String)
    withContext(Dispatchers.Main) {
        if (bitmap != null) {
            this@loadProfileImage.alpha = 0f
            this@loadProfileImage.setImageBitmap(bitmap)
            this@loadProfileImage.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
        } else {
            this@loadProfileImage.setImageResource(R.drawable.ic_person)
        }
    }
}


// Encode image (from URI) to Base64 for upload
suspend fun encodeImageToBase64(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        if (originalBitmap == null) {
            Log.e("ImageUtils", "❌ Failed to decode image from URI.")
            return@withContext null
        }

        val resized = Bitmap.createScaledBitmap(originalBitmap, 512, 512, true)
        val outputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()

        Base64.encodeToString(byteArray, Base64.DEFAULT)
    } catch (e: Exception) {
        Log.e("ImageUtils", "❌ Exception while encoding: ${e.message}", e)
        null
    }
}
