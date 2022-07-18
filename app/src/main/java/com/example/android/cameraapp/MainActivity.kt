package com.example.android.cameraapp

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.android.cameraapp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels(
        factoryProducer = {
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        val view = binding.root
        setContentView(view)

        viewModel.galleryList.observe(this) { list ->
            binding.textView.text = list.map { "$it\n" }.toString()
        }

        binding.button.setOnClickListener {
            dispatchTakePictureIntent()
        }
    }

    override fun onResume() {
        super.onResume()
        checkCameraPermission()
    }

    private lateinit var currentPhotoPath: File

    private val getResultData = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {

            val bitmapOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true

                val targetSize = Integer.max(outWidth, outHeight)
                var scaleFactor = 1
                if (targetSize > PICTURE_RESOLUTION_STANDARD) {
                    scaleFactor = targetSize / PICTURE_RESOLUTION_STANDARD
                }

                inJustDecodeBounds = false
                inSampleSize = scaleFactor
            }

            try {

                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val date = System.currentTimeMillis()
                val fileName = "$date.jpg"

                val dirDest = File(Environment.DIRECTORY_PICTURES)

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(MediaStore.MediaColumns.DATE_ADDED, date)
                    put(MediaStore.MediaColumns.DATE_MODIFIED, date)


                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "$dirDest${File.separator}")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val imageUri = contentResolver.insert(collection, contentValues)

                lifecycleScope.launch(Dispatchers.IO) {
                    imageUri?.let { outputUri ->
                        // 원본 이미지 그대로 저장하는 버전
//                        contentResolver.openFileDescriptor(Uri.fromFile(currentPhotoPath), "r")
//                            ?.use { inputFile ->
//                                val inputStream = FileInputStream(inputFile.fileDescriptor)
//                                contentResolver.openOutputStream(outputUri, "w")?.use { out ->
//                                    inputStream.copyTo(out)
//                                }
//                            }
                        contentResolver.openOutputStream(outputUri, "w")?.use { out ->
                            try {
                                BitmapFactory.decodeFile(
                                    currentPhotoPath.absolutePath,
                                    bitmapOptions
                                )?.also { bitmap ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                }
                            } catch (e: IOException) {

                            }
                        }

                        contentValues.clear()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                            contentResolver.update(outputUri, contentValues, null, null)
                        }
                    }
                }
            } catch (e: FileNotFoundException) {

            }
            viewModel.getAllPhotos()
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = this
        }
    }

    private fun dispatchTakePictureIntent() {
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("E", "gallery not_granted_storage")
            requestPermissions()
            return
        }

        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    // Error occurred while creating the File
                    null
                }
                // Continue only if the File was successfully created
                photoFile?.also { file ->
                    val photoURI =
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
                            Uri.fromFile(photoFile)
                        else FileProvider.getUriForFile(
                            this,
                            "$packageName.fileprovider",
                            file
                        )

                    takePictureIntent.flags =
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    getResultData.launch(takePictureIntent)
                }
            }
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ), // 1
            1001
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1001 -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED
                    && grantResults[2] == PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e("E", "PackageManager.PERMISSION_GRANTED $grantResults")
                    dispatchTakePictureIntent()
                    viewModel.getAllPhotos()
                }
            }
        }
    }

    private fun checkCameraPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("E", "not_granted_storage")
            return
        }
        viewModel.getAllPhotos()
    }

    companion object {
        const val PICTURE_RESOLUTION_STANDARD = 1280
    }
}