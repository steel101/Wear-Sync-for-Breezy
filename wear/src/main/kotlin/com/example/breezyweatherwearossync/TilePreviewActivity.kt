package com.example.breezyweatherwearossync

import android.os.Bundle
import androidx.activity.ComponentActivity

class TilePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This is just a dummy activity to help the system recognize the tile provider
        finish()
    }
}
