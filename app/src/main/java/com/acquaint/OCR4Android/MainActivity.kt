package com.acquaint.OCR4Android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.DisplayMetrics
import android.util.Log
import android.util.SparseArray
import android.view.View
import android.widget.TextView

import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.text.TextBlock
import com.google.android.gms.vision.text.TextRecognizer

import org.komamitsu.text_recognisition_sample.R

import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator

class MainActivity : AppCompatActivity() {

    private var imageUri: Uri? = null
    private var detectedTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        MY_PERMISSION_WRITE_EXTERNAL_STORAGE)
            }
        }

        findViewById<View>(R.id.choose_from_gallery).setOnClickListener {
            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(intent, REQUEST_GALLERY)
        }

        findViewById<View>(R.id.take_a_photo).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.CAMERA),
                            MY_PERMISSION_ACCESS_CAMERA)
                } else {
                    openCamera()
                }
            }
        }

        detectedTextView = findViewById(R.id.detected_text)
        detectedTextView!!.movementMethod = ScrollingMovementMethod()
    }

    private fun openCamera() {
        val filename = System.currentTimeMillis().toString() + ".jpg"

        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, filename)
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        val intent = Intent()
        intent.action = MediaStore.ACTION_IMAGE_CAPTURE
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        startActivityForResult(intent, REQUEST_CAMERA)
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            MY_PERMISSION_WRITE_EXTERNAL_STORAGE -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay! do the
                // calendar task you need to do.
            } else {
                // permission denied, boo! Disable the
                // functionality that depends on this permission.
            }
            MY_PERMISSION_ACCESS_CAMERA -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                // permission denied, boo! Disable the
                // functionality that depends on this permission.
            }
        }
    }

    private fun inspectFromBitmap(bitmap: Bitmap?) {
        val textRecognizer = TextRecognizer.Builder(this).build()
        try {
            if (!textRecognizer.isOperational) {
                AlertDialog.Builder(this).setMessage("Text recognizer could not be set up on your device").show()
                return
            }

            val frame = Frame.Builder().setBitmap(bitmap!!).build()
            val origTextBlocks = textRecognizer.detect(frame)
            val textBlocks = ArrayList<TextBlock>()
            for (i in 0 until origTextBlocks.size()) {
                val textBlock = origTextBlocks.valueAt(i)
                textBlocks.add(textBlock)
            }
            Collections.sort(textBlocks) { o1, o2 ->
                val diffOfTops = o1.boundingBox.top - o2.boundingBox.top
                val diffOfLefts = o1.boundingBox.left - o2.boundingBox.left
                if (diffOfTops != 0) {
                    diffOfTops
                } else diffOfLefts
            }

            val detectedText = StringBuilder()
            for (textBlock in textBlocks) {
                if (textBlock != null && textBlock.value != null) {
                    detectedText.append(textBlock.value)
                    detectedText.append("\n")
                }
            }

            detectedTextView!!.text = detectedText
        } finally {
            textRecognizer.release()
        }
    }

    private fun inspect(uri: Uri?) {
        var `is`: InputStream? = null
        var bitmap: Bitmap? = null
        try {
            `is` = contentResolver.openInputStream(uri!!)
            val options = BitmapFactory.Options()
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            options.inSampleSize = 2
            options.inScreenDensity = DisplayMetrics.DENSITY_LOW
            bitmap = BitmapFactory.decodeStream(`is`, null, options)
            inspectFromBitmap(bitmap)
        } catch (e: FileNotFoundException) {
            Log.w(TAG, "Failed to find the file: " + uri!!, e)
        } finally {
            bitmap?.recycle()
            if (`is` != null) {
                try {
                    `is`.close()
                } catch (e: IOException) {
                    Log.w(TAG, "Failed to close InputStream", e)
                }

            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_GALLERY -> if (resultCode == Activity.RESULT_OK) {
                inspect(data!!.data)
            }
            REQUEST_CAMERA -> if (resultCode == Activity.RESULT_OK) {
                if (imageUri != null) {
                    inspect(imageUri)
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    companion object {
        private val REQUEST_GALLERY = 0
        private val REQUEST_CAMERA = 1

        private val TAG = MainActivity::class.java!!.getSimpleName()
        private val MY_PERMISSION_WRITE_EXTERNAL_STORAGE = 100
        private val MY_PERMISSION_ACCESS_CAMERA = 101
    }
}
