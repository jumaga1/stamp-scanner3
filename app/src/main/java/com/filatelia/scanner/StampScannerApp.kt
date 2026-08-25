package com.filatelia.scanner

import android.app.Application
import com.filatelia.scanner.ai.AiRecognitionRepository
import com.filatelia.scanner.data.AppDatabase
import com.filatelia.scanner.data.StampRepository

class StampScannerApp : Application() {

    lateinit var stampRepository: StampRepository
        private set

    lateinit var aiRepository: AiRecognitionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        stampRepository = StampRepository(db.stampDao())
        aiRepository = AiRecognitionRepository()
    }
}
