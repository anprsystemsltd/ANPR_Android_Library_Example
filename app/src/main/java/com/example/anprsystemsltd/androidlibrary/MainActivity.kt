package com.example.anprsystemsltd.androidlibrary

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.anpr.sdk.mobile.j2ni.JavaToNative
import com.anpr.sdk.mobile.decoder.*
import com.anpr.sdk.mobile.license.LicenseManager
import kotlin.system.exitProcess
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File

@SuppressWarnings("deprecation")
class MainActivity : AppCompatActivity(), SurfaceHolder.Callback, Camera.PreviewCallback {

    private val javaToNative = JavaToNative()
    private val nativeOutputDecoder = NativeOutputDecoder.getInstance()
    private val licenseManager = LicenseManager()

    private var libraryInitialized = false
    private var libraryBusy = false

    private var camera: Camera? = null
    private var cameraPreviewWidth: Int = 0
    private var cameraPreviewHeight: Int = 0
    private var cameraPreviewFormat: Int = 0

    private var lastRecognizedString = ""
    private var recognizingConunter = 0
    private val results = mutableListOf<Pair<String, Long>>()

    var deviceId = ""
    private var licenceFoundOnServer = false

    private var infoDialog: InfoDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeActivity()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (libraryInitialized) {
            closeAnprNativeLibrary();
        }
    }

    override fun onBackPressed() {
        finish()
        exitProcess(1)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        initializeCamera(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        closeCamera()
    }

    override fun onPreviewFrame(data: ByteArray, camera: Camera) {
        processCameraFrame(data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.find { it != PackageManager.PERMISSION_GRANTED } != null) {
                showInfo("Without these permissions, the app cannot be used.")
            } else {
                recreate()
            }
        }
    }



    private fun initializeActivity() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        clearEnvironment()

        setContentView(R.layout.activity_main)

        if (!checkPermissions()) {
            askPermissions()
            return
        }

        deviceId = licenseManager.getDeviceId(this)
        title = "Android ID:$deviceId"

        licenseManager.processLicense(this) { success ->
            licenceFoundOnServer = success
            initializeAnprNativeLibrary()

            runOnUiThread {
                iwInfo.visibility = View.VISIBLE
                showInfoDialog()
            }
        }

        surfaceCamera.holder.also {
            it.addCallback(this)
            it.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        }

        iwInfo.setOnClickListener { showInfoDialog() }

    }

    private fun clearEnvironment() {
//        try { File("${filesDir.absolutePath}/anprlicense.txt").delete() } catch (e: Exception) {}
        try { File("/sdcard/class.txt").delete() } catch (e: Exception) {}
    }

    private fun checkPermissions() : Boolean = if (Build.VERSION.SDK_INT < 23) true else {
        permissions.find { permission ->
            checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        } == null
    }

/*
    The ANPR native so library file name must begin width "lib" and must end with ".so"
*/
    private fun initializeAnprNativeLibrary() {
        javaToNative.setLogListener {
            Log.d(LOG_TAG, "Log from native library:$it")
        }

    javaToNative.loadNativeLibrary("lib_anpr_Hungary.so") // you can give full file name
//        javaToNative.loadNativeLibrary("_anpr_Spain") // or you can give without "lib" and ".so"

        javaToNative.initNativeLibrary(this)

        libraryInitialized = true
    }

    private fun closeAnprNativeLibrary() {
        javaToNative.close()
    }

    private fun initializeCamera(holder: SurfaceHolder?) {
        var success = false
        try {
            camera = Camera.open()
            camera?.let { cam ->
                cam.parameters.let { par ->
                    val cameraResolution = par.supportedPreviewSizes.find {
                        it.width == 640
                    }
                    cameraResolution?.let {
                        cameraPreviewWidth = cameraResolution.width
                        cameraPreviewHeight = cameraResolution.height
                        cameraPreviewFormat = par.previewFormat
                        par.setPreviewSize(cameraPreviewWidth, cameraPreviewHeight)
                        cam.parameters = par
                        cam.setPreviewCallback(this)
                        cam.setPreviewDisplay(holder)
                        cam.startPreview()
                        success = true
                    }
                }
            }
        } catch (e: Exception) {}
        if (!success) showInfo("Error to initialize camera!")
    }

    private fun closeCamera() {
        camera?.apply {
            setPreviewCallback(null);
            stopPreview();
            release();
        }
    }

    private fun processCameraFrame(data: ByteArray) {
        if (!libraryInitialized || libraryBusy) return
        libraryBusy = true

        val imageData = data.copyOfRange(0, cameraPreviewWidth * cameraPreviewHeight)
        val anprOut = javaToNative.processImage(
            imageData,
            cameraPreviewWidth,
            cameraPreviewHeight,
            0,
            0,
            1,
            1)
        nativeOutputDecoder.refreshFromBuffer(anprOut)

        if (!nativeOutputDecoder.isValid) {
            camera?.stopPreview()
            showInfo("Invalid data from ANPPR library!")
        }

        if (nativeOutputDecoder.numberOfChars > 0) {
            val recognizedString = nativeOutputDecoder.plate
            if (recognizedString == lastRecognizedString) {
                recognizingConunter++
                if (recognizingConunter >= 2) {
                    val result = recognizedString + " - " + nativeOutputDecoder.syntaxText
                    val now = System.currentTimeMillis()
                    results.removeAll { it.second < System.currentTimeMillis() }
                    results.find { it.first == result }?:let {
                        results.add(Pair(result, now + 5000))
//                        saveFrameImage(data, result + "_" + now.toString() + ".jpg")
                        title = result
                    }
                    lastRecognizedString = ""
                    recognizingConunter = 0
                }
            } else {
                lastRecognizedString = recognizedString
                recognizingConunter = 0
            }
        }
        libraryBusy = false
    }

    private fun askPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(permissions.toTypedArray(), PERMISSION_REQUEST)
        }
    }

    private fun showInfoDialog() {
        val closeListener = object : InfoDialog.CloseListener {
            override fun onClosed() {
                infoDialog = null
            }
        }
        infoDialog?:let {
            val params = InfoDialog.Parameter(
                libraryInterface = javaToNative,
                deviceId = deviceId,
                licenceOnServer = licenceFoundOnServer
            )
            infoDialog = InfoDialog(
                this,
                closeListener,
                params
            ).apply { show() }
        }
    }

    private fun showInfo(msg: String, fatalError: Boolean = true) {
        AlertDialog.Builder(this).apply {
            setMessage(msg)
            setPositiveButton("OK") { _, _ ->
                if (fatalError) {
                    finish()
                    exitProcess(1)
                }
            }
        }.create().show()
    }

    companion object {
        private const val LOG_TAG = "DEBINFO"

        private const val PERMISSION_REQUEST = 1

        private val permissions = listOf(
            Manifest.permission.READ_PHONE_STATE,   // need if API level < 29
            Manifest.permission.CAMERA
        )
    }



}