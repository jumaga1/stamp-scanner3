package com.filatelia.scanner

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

class StampScannerApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Limpieza de imágenes temporales de escaneos previos al iniciar la app
        cleanStampsCache()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15) // Máximo 15% de RAM
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(25L * 1024 * 1024) // Límite estricto de 25 MB en disco
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }

    private fun cleanStampsCache() {
        try {
            val dir = File(cacheDir, "stamps_cache")
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    // Eliminar temporales con más de 24 horas
                    if (System.currentTimeMillis() - file.lastModified() > 86400000L) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
