package com.burhan.mscameraxsampleapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.Toast
import android.widget.ImageButton
import android.widget.SeekBar
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.burhan.mscameraxsampleapp.databinding.ActivityMainBinding
import java.lang.reflect.Type

import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private  var camera: Camera? = null

    private lateinit var viewFinder : View
    private lateinit var btnTakePhoto : ImageButton
    private lateinit var flashToggleButton : ImageButton
    private lateinit var zoomPlusButton : ImageButton
    private lateinit var zoomMinusButton : ImageButton
    private lateinit var normalPhotoButton : Button
    private lateinit var bokehPhotoButton : Button
    private lateinit var exposureSeekbar : SeekBar
    private var bokehCameraSelector: CameraSelector? = null

    private var bokehModeEnabled = false

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewFinder= binding.viewFinder
        btnTakePhoto = binding.btnTakePhoto
        flashToggleButton = binding.flashToggleButton
        zoomPlusButton = binding.zoomPlusButton
        zoomMinusButton = binding.zoomMinusButton
        normalPhotoButton = binding.normalPhotoBtn
        bokehPhotoButton = binding.bokehPhotoBtn
        exposureSeekbar = binding.exposureSeekBar

        supportActionBar?.hide()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                window.navigationBarColor = getColor(R.color.white)

                it.hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }

        if (allPermissionGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, Constants.REQUIRED_PERMISSION, Constants.REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun toggleFlash() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({

            val cameraControl = camera?.cameraControl
            val cameraInfo = camera?.cameraInfo
            val currFlashStatus = cameraInfo?.torchState

            if (currFlashStatus?.value == 1) {
                cameraControl?.enableTorch(false)
            } else {
                cameraControl?.enableTorch(true)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun adjustZoom(inc: Boolean){

        val cameraControl = camera?.cameraControl
        val cameraInfo = camera?.cameraInfo

        val maxZoom = cameraInfo?.zoomState?.value?.maxZoomRatio
        val minZoom = cameraInfo?.zoomState?.value?.minZoomRatio
        val currZoom = cameraInfo?.zoomState?.value?.zoomRatio

        if(inc){
            if(currZoom == maxZoom){
                Toast.makeText(this,"Cannot zoom in further",Toast.LENGTH_SHORT).show()
            }
            if (currZoom != null) {
                if (maxZoom != null) {
                    cameraControl?.setZoomRatio((currZoom+(maxZoom- minZoom!!)/10.0).toFloat())
                }
            }
        }else{
            if(currZoom == minZoom){
                Toast.makeText(this,"Cannot zoom out further",Toast.LENGTH_SHORT).show()
            }
            if (currZoom != null) {
                if (maxZoom != null) {
                    cameraControl?.setZoomRatio((currZoom-(maxZoom- minZoom!!)/10.0).toFloat())
                }
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview: Preview = Preview.Builder().build()

            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            imageCapture = ImageCapture.Builder().build()

            var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA


            Log.d(Constants.TAG, "startCamera: $cameraSelector")
            try {
                Toast.makeText(this, "Normal Camera", Toast.LENGTH_SHORT).show()
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                bokehModeEnabled = false
                startOtherFunctionality()
            } catch (e: Exception) {
                Log.d(Constants.TAG, "StartCamera Fail:", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }


    private fun startOtherFunctionality(){
        // Take photo when capture button is click.
        btnTakePhoto.setOnClickListener {
            takePhoto()
        }

        // Toggle the flash light.
        flashToggleButton.setOnClickListener {
            toggleFlash()
        }

        zoomMinusButton.setOnClickListener{
            adjustZoom(false)
        }

        zoomPlusButton.setOnClickListener{
            adjustZoom(true)
        }

        normalPhotoButton.setOnClickListener{
            enableNormalMode()
        }

        bokehPhotoButton.setOnClickListener{
            enableBokehMode()
        }

        var exposureState = camera?.cameraInfo?.exposureState
        binding.exposureSeekBar

        val isEnabled = exposureState?.isExposureCompensationSupported
        val maxExposure = exposureState?.exposureCompensationRange?.upper
        val minExposure = exposureState?.exposureCompensationRange?.lower
        val progressExposure = exposureState?.exposureCompensationIndex

        exposureSeekbar.max = maxExposure?:0
        exposureSeekbar.progress = progressExposure?:0

        exposureSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            @RequiresApi(Build.VERSION_CODES.P)

            var startPoint = 0
            var endPoint = 0

            override fun onProgressChanged(p0: SeekBar?, currentValue: Int, p2: Boolean) {
                camera?.cameraControl?.setExposureCompensationIndex(currentValue)
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
                if (p0 != null) {
                    startPoint = p0.progress
                }
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                if (p0 != null) {
                    endPoint = p0.progress
                }
            }

        })
    
        viewFinder.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                Log.d(Constants.TAG, "onTouch: ${event.x}")

                val meteringPoint = binding.viewFinder.meteringPointFactory
                        .createPoint(event.x,event.y)

                val action = FocusMeteringAction.Builder(meteringPoint).build()
                val result = camera?.cameraControl?.startFocusAndMetering(action)

                result?.addListener({
                    val isFocusSuccessful = result.get().isFocusSuccessful
                    if(isFocusSuccessful){
                        Toast.makeText(this@MainActivity,"Focusing",Toast.LENGTH_SHORT).show()
                    }else{
                        Toast.makeText(this@MainActivity,"Unable to Focusing",Toast.LENGTH_SHORT).show()
                    }

                },ContextCompat.getMainExecutor(this@MainActivity))


                return v?.onTouchEvent(event) ?: true
            }
        })
    }

    @SuppressLint("ResourceAsColor")
    private fun enableBokehMode() {
        if(bokehModeEnabled){
            return
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
        val preview: Preview = Preview.Builder().build()

        preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        imageCapture = ImageCapture.Builder().build()
        if(bokehCameraSelector == null){

            cameraProviderFuture.addListener({
                var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                Log.d(Constants.TAG, "startCamera: $cameraSelector")

                val future = ExtensionsManager.getInstanceAsync(this, cameraProvider)
                future.addListener({
                    val extensionsManager = future.get()
                    if (extensionsManager.isExtensionAvailable(
                            cameraSelector,
                            ExtensionMode.BOKEH
                        )) {
                        bokehCameraSelector = extensionsManager.getExtensionEnabledCameraSelector(
                            cameraSelector,
                            ExtensionMode.BOKEH
                        )
                        try {
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(this,
                                bokehCameraSelector!!, preview, imageCapture)
                            bokehModeEnabled = true

                            bokehPhotoButton.setTextColor(R.color.white)
                            bokehPhotoButton.setTypeface(null,Typeface.BOLD)

                            normalPhotoButton.setTextColor(R.color.black)
                            normalPhotoButton.setTypeface(null, Typeface.NORMAL)

                            Toast.makeText(this, "Bokeh Mode Enabled.", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.d(Constants.TAG, "Bokeh Camera Start Fail:", e)
                        }
                    } else {
                        Toast.makeText(this, "Bokeh Extension Not Available!!", Toast.LENGTH_SHORT).show()
                    }

                }, ContextCompat.getMainExecutor(baseContext))
            }, ContextCompat.getMainExecutor(this))
        }else{
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this,
                    bokehCameraSelector!!, preview, imageCapture)
                bokehModeEnabled = true

                bokehPhotoButton.setTextColor(R.color.white)
                bokehPhotoButton.setTypeface(null,Typeface.BOLD)

                normalPhotoButton.setTextColor(R.color.black)
                normalPhotoButton.setTypeface(null, Typeface.NORMAL)

                Toast.makeText(this, "Bokeh Mode Enabled.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.d(Constants.TAG, "Bokeh Camera Start Fail:", e)
            }
        }

    }

    @SuppressLint("ResourceAsColor")
    private fun enableNormalMode() {
        if(!bokehModeEnabled){
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({

            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview: Preview = Preview.Builder().build()

            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            imageCapture = ImageCapture.Builder().build()

            var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            Log.d(Constants.TAG, "startCamera: $cameraSelector")

            try {
                Toast.makeText(this, "Normal Camera", Toast.LENGTH_SHORT).show()
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                bokehModeEnabled = false

                normalPhotoButton.setTextColor(R.color.white)
                normalPhotoButton.setTypeface(null,Typeface.BOLD)

                bokehPhotoButton.setTextColor(R.color.black)
                bokehPhotoButton.setTypeface(null, Typeface.NORMAL)
            } catch (e: Exception) {
                Log.d(Constants.TAG, "StartCamera Fail:", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val orientationEventListener = object : OrientationEventListener(this){
            override fun onOrientationChanged(orientation: Int) {
                val rotation: Int = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }

                imageCapture.targetRotation = rotation
            }
        }

        orientationEventListener.enable()


        val name = SimpleDateFormat(Constants.FILE_NAME_FORMAT, Locale.getDefault())
            .format(System.currentTimeMillis())

        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
        }

        val outputOption = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOption,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(Constants.TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo Saved!"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(Constants.TAG, msg)
                }
            }
        )

    }

    private fun allPermissionGranted(): Boolean {
        for (permission in Constants.REQUIRED_PERMISSION) {
            if (ActivityCompat.checkSelfPermission(
                    baseContext,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.REQUEST_CODE_PERMISSIONS) {
            if (allPermissionGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permission not Granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    object Constants {
        const val TAG = "cameraX"
        const val FILE_NAME_FORMAT = "yy-MM-dd-HH-mm-ss-sss"
        const val REQUEST_CODE_PERMISSIONS = 123
        val REQUIRED_PERMISSION = arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

