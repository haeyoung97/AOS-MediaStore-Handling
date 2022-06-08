package com.example.android.cameraapp

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import com.example.android.cameraapp.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
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

    private val getResultData = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.getAllPhotos()

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, mCurrentPhotoPath.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpg")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val collection = MediaStore.Images.Media
                .getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val item = contentResolver.insert(collection, values)!!

            contentResolver.openFileDescriptor(item, "w", null).use {
                // write something to OutputStream
                FileOutputStream(it!!.fileDescriptor).use { outputStream ->
                    val imageInputStream = mCurrentPhotoPath.inputStream()
                    while (true) {
                        val data = imageInputStream.read()
                        if (data == -1) {
                            break
                        }
                        outputStream.write(data)
                    }
                    imageInputStream.close()
                    outputStream.close()
                }
            }

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(item, values, null, null)
        }
    }

    private lateinit var mCurrentPhotoPath: File

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
//        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
//        getResultData.launch(intent)

        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        Log.e("TAG", "takePictureIntent call")
        if (true || takePictureIntent.resolveActivity(packageManager) != null) {
            Log.e("TAG", "takePictureIntent init")

            // 촬영한 사진을 저장할 파일 생성
            var photoFile: File? = null
            try {
                Log.e("TAG", "takePictureIntent try")
                val storageDir : File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

                //임시촬영파일 세팅
                val timeStamp: String = SimpleDateFormat("yyyyMMdd").format(Date())
                val imageFileName = "Capture_" + timeStamp + "_" //ex) Capture_20201206_
                val tempImage: File = File.createTempFile(
                    imageFileName,  /* 파일이름 */
                    ".jpg",  /* 파일형식 */
                    storageDir  /* 경로 */
                )

                // ACTION_VIEW 인텐트를 사용할 경로 (임시파일의 경로)
                mCurrentPhotoPath = tempImage
                photoFile = tempImage
                Log.e("TAG", "takePictureIntent photoFile ${photoFile.absolutePath}")
            } catch (e: IOException) {
                //에러 로그는 이렇게 관리하는 편이 좋다.
                Log.e("TAG", "파일 생성 에러!", e)
            }

            //파일이 정상적으로 생성되었다면 계속 진행
            Log.e("TAG", "파일 ${photoFile?.absolutePath}")
            if (photoFile != null) {
                //Uri 가져오기
                val photoURI: Uri = FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    photoFile
                )
                //인텐트에 Uri담기
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)

                //인텐트 실행
                getResultData.launch(takePictureIntent)
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
}