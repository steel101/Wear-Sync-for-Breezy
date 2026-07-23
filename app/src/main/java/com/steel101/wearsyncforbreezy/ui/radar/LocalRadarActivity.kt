package com.steel101.wearsyncforbreezy.ui.radar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class LocalRadarActivity : ComponentActivity() {
    companion object {
        fun start(context: Context, latitude: Double, longitude: Double, cityName: String) {
            val intent = Intent(context, LocalRadarActivity::class.java).apply {
                putExtra("latitude", latitude)
                putExtra("longitude", longitude)
                putExtra("cityName", cityName)
            }
            context.startActivity(intent)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)
        val cityName = intent.getStringExtra("cityName") ?: "Radar"

        setContent {
            val prefs = remember { getSharedPreferences("radar_prefs", Context.MODE_PRIVATE) }
            var resetTrigger by remember { mutableLongStateOf(0L) }
            var activeLayer by remember { mutableStateOf("radar") }
            var showLabels by remember { mutableStateOf(prefs.getBoolean("show_labels", true)) }
            var showLegend by remember { mutableStateOf(prefs.getBoolean("show_legend", true)) }
            var mapStyle by remember { mutableStateOf(prefs.getString("map_style", "Satellite") ?: "Satellite") }

            val effectiveShowLabels = if (mapStyle == "Standard") false else showLabels
            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = cityName, color = Color.White) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                                }
                            },
                            actions = {
                                IconButton(onClick = { 
                                    showLegend = !showLegend
                                    prefs.edit().putBoolean("show_legend", showLegend).apply()
                                }) {
                                    Icon(imageVector = if (showLegend) Icons.Filled.Info else Icons.Outlined.Info, contentDescription = "Legend", tint = Color.White)
                                }
                                IconButton(onClick = { 
                                    showLabels = !showLabels
                                    prefs.edit().putBoolean("show_labels", showLabels).apply()
                                }, enabled = mapStyle != "Standard") {
                                    Icon(imageVector = if (effectiveShowLabels) Icons.Default.Label else Icons.Default.LabelOff, contentDescription = "Labels", tint = if (mapStyle != "Standard") Color.White else Color.White.copy(alpha = 0.3f))
                                }
                                var styleMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { styleMenu = true }) {
                                        Icon(imageVector = Icons.Default.Map, contentDescription = "Style", tint = Color.White)
                                    }
                                    DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                                        DropdownMenuItem(text = { Text("Satellite") }, onClick = { mapStyle = "Satellite"; prefs.edit().putString("map_style", "Satellite").apply(); styleMenu = false })
                                        DropdownMenuItem(text = { Text("Standard") }, onClick = { mapStyle = "Standard"; prefs.edit().putString("map_style", "Standard").apply(); styleMenu = false })
                                        DropdownMenuItem(text = { Text("Dark") }, onClick = { mapStyle = "Dark"; prefs.edit().putString("map_style", "Dark").apply(); styleMenu = false })
                                    }
                                }
                                IconButton(onClick = { resetTrigger = System.currentTimeMillis() }) {
                                    Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Center", tint = Color.White)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F1B2F))
                        )
                    }
                ) { paddings ->
                    LiveRadarMap(
                        latitude = latitude,
                        longitude = longitude,
                        modifier = Modifier.fillMaxSize().padding(paddings),
                        resetTrigger = resetTrigger,
                        showLabels = effectiveShowLabels,
                        mapStyle = mapStyle,
                        showLegend = showLegend,
                        activeLayer = activeLayer
                    )
                }
            }
        }
    }
}
