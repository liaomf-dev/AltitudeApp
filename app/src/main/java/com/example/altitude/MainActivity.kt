package com.example.altitude

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.location.altitude.AltitudeConverter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), LocationListener {

    private lateinit var timeText: TextView
    private lateinit var altitudeText: TextView
    private lateinit var accuracyText: TextView
    private lateinit var regionText: TextView
    private lateinit var coordText: TextView
    private lateinit var statusText: TextView

    private lateinit var locationManager: LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var lastGeocoded: Location? = null

    private val clockTick = object : Runnable {
        override fun run() {
            timeText.text = timeFormat.format(Date())
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        timeText = findViewById(R.id.timeText)
        altitudeText = findViewById(R.id.altitudeText)
        accuracyText = findViewById(R.id.accuracyText)
        regionText = findViewById(R.id.regionText)
        coordText = findViewById(R.id.coordText)
        statusText = findViewById(R.id.statusText)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        findViewById<Button>(R.id.refreshButton).setOnClickListener {
            lastGeocoded = null
            altitudeText.text = "定位中…"
            startLocating()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(clockTick)
        startLocating()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockTick)
        locationManager.removeUpdates(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdown()
    }

    // ---------- 權限與定位 ----------

    private fun hasPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startLocating() {
        if (!hasPermission()) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), REQ_LOCATION
            )
            return
        }
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            statusText.text = "請開啟手機的「定位 / GPS」功能"
            return
        }
        statusText.text = "GPS 定位中，請在室外或靠窗以取得較準的海拔"
        try {
            locationManager.removeUpdates(this)
            // 海拔只能由 GPS 取得，網路定位沒有海拔
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 2000L, 0f, this, Looper.getMainLooper()
            )
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onLocationChanged(it) }
        } catch (e: SecurityException) {
            statusText.text = "沒有定位權限"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) {
            if (hasPermission()) startLocating()
            else {
                altitudeText.text = "—"
                statusText.text = "需要「精確位置」權限才能測量海拔"
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        // Android 14 以上：用系統內建資料把 GPS 高度換算成「海平面高度」(MSL)
        // 這需要讀取資料檔，放到背景執行緒
        worker.execute {
            val loc = Location(location)
            if (Build.VERSION.SDK_INT >= 34 && loc.hasAltitude() && !loc.hasMslAltitude()) {
                try {
                    AltitudeConverter().addMslAltitudeToLocation(this, loc)
                } catch (_: Throwable) {
                    // 換算失敗就退回 GPS 原始高度
                }
            }
            handler.post { showLocation(loc) }
        }
    }

    private fun showLocation(loc: Location) {
        if (Build.VERSION.SDK_INT >= 34 && loc.hasMslAltitude()) {
            altitudeText.text = String.format(Locale.US, "%.0f 公尺", loc.mslAltitudeMeters)
            val acc = when {
                loc.hasMslAltitudeAccuracy() -> loc.mslAltitudeAccuracyMeters
                loc.hasVerticalAccuracy() -> loc.verticalAccuracyMeters
                else -> 0f
            }
            accuracyText.text = if (acc > 0) String.format(Locale.US, "誤差約 ±%.0f 公尺（海平面高度）", acc) else "海平面高度"
        } else if (loc.hasAltitude()) {
            altitudeText.text = String.format(Locale.US, "%.0f 公尺", loc.altitude)
            accuracyText.text = "GPS 橢球高（台灣地區約比實際海拔高 15–25 公尺）"
        } else {
            altitudeText.text = "定位中…"
            accuracyText.text = "尚未取得 GPS 高度"
        }
        coordText.text = String.format(Locale.US, "%.5f, %.5f", loc.latitude, loc.longitude)
        statusText.text = "GPS 水平誤差約 ±${loc.accuracy.toInt()} 公尺"

        // 移動超過 200 公尺才重新查詢地區，避免頻繁連網
        val prev = lastGeocoded
        if (prev == null || prev.distanceTo(loc) > 200) {
            lastGeocoded = loc
            lookupRegion(loc)
        }
    }

    // ---------- 地區反查 ----------

    private fun lookupRegion(loc: Location) {
        if (!Geocoder.isPresent()) {
            regionText.text = "此手機不支援地區查詢"
            return
        }
        val geocoder = Geocoder(this, Locale.TRADITIONAL_CHINESE)
        if (Build.VERSION.SDK_INT >= 33) {
            geocoder.getFromLocation(loc.latitude, loc.longitude, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    handler.post { regionText.text = formatRegion(addresses.firstOrNull()) }
                }
                override fun onError(errorMessage: String?) {
                    handler.post { regionText.text = "查詢失敗（需要網路）" }
                }
            })
        } else {
            worker.execute {
                val text = try {
                    @Suppress("DEPRECATION")
                    formatRegion(geocoder.getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull())
                } catch (_: Exception) {
                    "查詢失敗（需要網路）"
                }
                handler.post { regionText.text = text }
            }
        }
    }

    private fun formatRegion(a: Address?): String {
        if (a == null) return "查無地區資料"
        val parts = listOf(a.adminArea, a.subAdminArea, a.locality, a.subLocality)
            .filterNotNull().filter { it.isNotBlank() }.distinct()
        return when {
            parts.isNotEmpty() -> parts.joinToString(" ")
            a.countryName != null -> a.countryName
            else -> "查無地區資料"
        }
    }

    override fun onProviderDisabled(provider: String) {
        statusText.text = "GPS 已關閉，請開啟定位"
    }

    override fun onProviderEnabled(provider: String) {
        startLocating()
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    companion object {
        private const val REQ_LOCATION = 1
    }
}
