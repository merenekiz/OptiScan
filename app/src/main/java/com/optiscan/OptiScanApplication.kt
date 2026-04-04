package com.optiscan

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class OptiScanApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        loadOpenCV()
        createAppDirs()
    }

    private fun loadOpenCV() {
        try {
            System.loadLibrary("opencv_java4")
            isOpenCvLoaded = true
            android.util.Log.i("OptiScan", "OpenCV loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            isOpenCvLoaded = false
            android.util.Log.w("OptiScan", "OpenCV native lib not found: ${e.message}")
        }
    }

    private fun createAppDirs() {
        File(filesDir, "exports").mkdirs()
        File(cacheDir, "scans").mkdirs()
    }

    companion object {
        var isOpenCvLoaded: Boolean = false
            private set
    }
}
