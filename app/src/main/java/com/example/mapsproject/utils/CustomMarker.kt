package com.example.mapsproject.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

object CustomMarker {
    // Create a Custom Marker with Text
    fun createTextMarker(context: android.content.Context, text: String): BitmapDescriptor {
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 40f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)

        val bitmap = Bitmap.createBitmap(
            textBounds.width() + 40,
            textBounds.height() + 20,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        canvas.drawText(text, 20f, textBounds.height().toFloat() + 10, paint)


        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}