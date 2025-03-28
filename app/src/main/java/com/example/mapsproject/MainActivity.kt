package com.example.mapsproject

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.MenuRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.example.mapsproject.databinding.ActivityMainBinding
import com.example.mapsproject.services.MapServices
import com.example.mapsproject.services.VibrationService
import com.example.mapsproject.utils.CalculatingCentroid.calculateCentroid
import com.example.mapsproject.utils.CalculatingCentroid.calculatePolygonArea
import com.example.mapsproject.utils.CalculatingNearestEdges.calculateDistance
import com.example.mapsproject.utils.CalculatingNearestEdges.getNearestPointOnSegment
import com.example.mapsproject.utils.CustomMarker.createTextMarker
import com.example.mapsproject.utils.IsPolygon.isPolygon
import com.example.mapsproject.view_models.MapViewModel
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.maps.android.ktx.awaitMap
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import pub.devrel.easypermissions.EasyPermissions
@AndroidEntryPoint
class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {

    private var animationFlag = true
    private var manualMarkerList = mutableListOf<LatLng>()
    private lateinit var gMap: GoogleMap
    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 123
    private lateinit var locationManager: LocationManager
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
   private val latLangLiveData = MutableLiveData<LatLng>()
    private val markersList = mutableListOf<Marker>()
    private var polyline: PolylineOptions? = null // To draw lines between markers
    private var dottedLine: Polyline? = null
    private var nearestPointMarker: Marker? = null
    private var distanceMarker: Marker? = null
    private var serviceStarted = false
    private val handler = Handler(Looper.getMainLooper())
    private var progress = 0


//    state
    private var inCalculateTotalArea = false
    private var inNearestBoundary = false
    private var inNearestEdge = false
    private var inCurrentToCenter = false

     private val viewModel : MapViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        lifecycleScope.launch {
            val map = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
            gMap = map?.awaitMap() ?: return@launch
            applyMapStyle()
            checkLocationPermission()
            notificationPermissionEnable()
            markerState()
            calculationState()
            setUpIsLive()
            draggableEvent()
            initObservers()
        }


        //Menu

        binding.menu.setOnClickListener { v: View ->
            showMenu(v, R.menu.options_menu)
        }



        //calculation cancel button
        binding.cancelCard.setOnClickListener {
            removeDottedLineAndMarkers()
            viewModel.setCalculationFlagFalse()
            setAllStatesFalse()
        }



        //Moving to the current location
        binding.currentLocation.setOnClickListener {
            animationFlag = true
            checkLocationPermission()
        }



        // Clear Single Marker
        binding.clearMarker.setOnClickListener {
            if (markersList.size>1){
            clearLastMarker()
            drawPolygon()
        }else{
           clearAllMarkers()
            }
        }

        // Clears All markers
        binding.clearAllMarker.setOnClickListener {
             clearAllMarkers()
        }

        //Shut down all markers activity's
        binding.closeMarker.setOnClickListener {
            viewModel.setMarkerFlagFalse()
            clearAllMarkers()
            gMap.setOnMapClickListener(null)
        }
    }

    override fun onResume() {
        super.onResume()
        if(serviceStarted)startService(Intent(this,VibrationService::class.java))
    }
    override fun onPause() {
        super.onPause()
        stopService(Intent(this,VibrationService::class.java))
    }

    private fun initObservers() {
        viewModel.isLive.observe(this){live ->

            if(live && inCalculateTotalArea)settingUpCalculateTotalAreaState()
            if(live && inNearestEdge)settingUpNearestEdgeState()
            if(live && inNearestBoundary)settingUpNearestMarkerState()
            if(live && inCurrentToCenter)settingUpCurrentToCenterLocationState()

            Log.d("valures","$inCurrentToCenter  $inNearestEdge $inNearestBoundary $inCalculateTotalArea" )

        }
    }

    private fun notificationPermissionEnable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!EasyPermissions.hasPermissions(this, Manifest.permission.POST_NOTIFICATIONS)) {
                EasyPermissions.requestPermissions(
                    this,
                    "This app needs notification permission to show notifications",
                    1001,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

    private fun setAllStatesFalse() {
        viewModel.setNearestMarkerLiveFlagFalse()
        viewModel.setNearestBoundaryLiveFlagFalse()
        viewModel.setCurrentToCenterLiveFlagFalse()
        viewModel.setCalculateAreaLiveFlagFalse()
    }

    private fun setUpIsLive() {
        binding.liveButton.isChecked = false
        //Live fetching
        binding.liveButton.setOnCheckedChangeListener{ _, isChecked ->
            if(isChecked){

                viewModel.setLiveFlagTrue()

            }else{
                stopService(Intent(this,VibrationService::class.java))
                startProgressAnimation()
                viewModel.setLiveFlagFalse()

            }
            Log.d("livedata","${viewModel.isLive.value}")
        }

    }

    private fun startProgressAnimation() {
        progress = 0
        binding.liveButton.isEnabled = false // Disable the switch during progress

        // Animate Progress
        handler.post(object : Runnable {
            override fun run() {
                if (progress < 100) {
                    progress += 2
                    // Apply progress as track tint to visualize progress
                    val progressFraction = progress / 100f
                    val color = interpolateColor(0xFFB0BEC5.toInt(), 0xFF4CAF50.toInt(), progressFraction)
                    binding.liveButton.trackTintList = android.content.res.ColorStateList.valueOf(color)
                    handler.postDelayed(this, 100)
                } else {
                    binding.liveButton.isEnabled = true
                    binding.liveButton.trackTintList = null // Reset to default color
                }
            }
        })
    }

    private fun interpolateColor(colorStart: Int, colorEnd: Int, fraction: Float): Int {
        val startA = (colorStart shr 24 and 0xff)
        val startR = (colorStart shr 16 and 0xff)
        val startG = (colorStart shr 8 and 0xff)
        val startB = (colorStart and 0xff)

        val endA = (colorEnd shr 24 and 0xff)
        val endR = (colorEnd shr 16 and 0xff)
        val endG = (colorEnd shr 8 and 0xff)
        val endB = (colorEnd and 0xff)

        val resultA = (startA + (fraction * (endA - startA)).toInt()) shl 24
        val resultR = (startR + (fraction * (endR - startR)).toInt()) shl 16
        val resultG = (startG + (fraction * (endG - startG)).toInt()) shl 8
        val resultB = (startB + (fraction * (endB - startB)).toInt())

        return resultA or resultR or resultG or resultB
    }
    private fun calculationState() {
        //calculation state
        viewModel.calculationLiveData.observe(this){ flag->
            binding.let{
                it.cancelCard.visibility = if(flag) View.VISIBLE else View.GONE
                it.dataCard.visibility = if(flag) View.VISIBLE else View.GONE
            }
        }

    }

    private fun markerState() {
        //handling markers options
        viewModel.optionLiveData.observe(this) { flag ->
            binding.let {
                it.optionsCard.visibility = if (flag) View.VISIBLE else View.GONE
        //                it.addMarkers.visibility = if (flag) View.GONE else View.GONE
                if (flag) setUpOnClickListener() else gMap.setOnMapClickListener(null)
            }
        }

    }

    // Clear one marker in the map
    private fun clearLastMarker() {
        if (markersList.isNotEmpty() && manualMarkerList.isNotEmpty()) {
            val lastMarker = markersList.removeAt(markersList.size - 1) // Remove the last marker from the list
            lastMarker.remove() // Remove the marker from the map
            manualMarkerList.removeAt(manualMarkerList.size - 1) // Remove the last LatLng from the list
            // Redraw lines and polygon
            drawLinesBetweenMarkers()
            if (manualMarkerList.size >= 3) {
                drawPolygon()
            } else {
                // Clear the polygon if there are fewer than 3 markers
                gMap.clear()
                for (marker in manualMarkerList) {
                    gMap.addMarker(MarkerOptions().position(marker).title("Marker"))
                }
            }
        } else {
            Toast.makeText(this, "No markers to clear!", Toast.LENGTH_SHORT).show()
        }
    }

    //clear single marker function
    private fun clearAllMarkers() {
        manualMarkerList.clear()
        markersList.clear()
        gMap.clear()
    }

   //show menu function
    @SuppressLint("DefaultLocale")
    private fun showMenu(v: View, @MenuRes menuRes: Int) {
        val popup = PopupMenu(this, v)
        popup.menuInflater.inflate(menuRes, popup.menu)

        popup.setOnMenuItemClickListener { menuItem: MenuItem ->
            when(menuItem.itemId) {
                R.id.addMarkersInMap -> {
                    allStateOff()
                    addMarkersInTheMap()
                    true
                }
                R.id.calculate_total_area ->{
                    if(manualMarkerList.size<3) {
                        Toast.makeText(this, "Need at least 3 Markers",Toast.LENGTH_SHORT).show()
                        return@setOnMenuItemClickListener true
                    }

                        if (!inCalculateTotalArea)settingUpCalculateTotalAreaIn()

                    Log.d("valures","$inCurrentToCenter  $inNearestEdge $inNearestBoundary $inCalculateTotalArea" )

                    calculateTheToatalArea()

                    true
                }

                R.id.find_nearestEdge -> {
                    if(manualMarkerList.size<3) {
                        Toast.makeText(this, "Need at least 3 Markers",Toast.LENGTH_SHORT).show()
                        return@setOnMenuItemClickListener true
                    }
                    if (!inNearestBoundary)settingUpNearestBoundaryIn()
                    findNearestBoundary()

                    true
                }
                R.id.find_nearest_marker ->{
                    if(manualMarkerList.size<3) {
                        Toast.makeText(this, "Need at least Markers",Toast.LENGTH_SHORT).show()
                        return@setOnMenuItemClickListener true
                    }
                    if (!inNearestEdge)settingUpNearestMarkerIn()
                    findNearestMarker()
                    true
                }
                R.id.current_to_center_point ->{
                    if(manualMarkerList.size<3) {
                        Toast.makeText(this, "Need at least Markers",Toast.LENGTH_SHORT).show()
                        return@setOnMenuItemClickListener true
                    }

                    if (!inCurrentToCenter)settingUpCurrentToCenterIn()
                    findCurrentPointToCenterPoint()
                    true
                }

                else -> false
            }
        }
        popup.setOnDismissListener {
            // Respond to popup being dismissed.
        }
        // Show the popup menu.
        popup.show()
    }
    private fun settingUpCurrentToCenterIn() {
        inCalculateTotalArea = false
        inNearestEdge = false
        inNearestBoundary = false
        inCurrentToCenter = true
    }
    private fun settingUpNearestMarkerIn() {
        inCalculateTotalArea = false
        inNearestEdge = false
        inNearestBoundary = true
        inCurrentToCenter = false
    }

    private fun settingUpNearestBoundaryIn() {
        inCalculateTotalArea = false
        inNearestEdge = false
        inNearestBoundary = true
        inCurrentToCenter = false
    }

    private fun settingUpCalculateTotalAreaIn() {
        inCalculateTotalArea = true
        inNearestEdge = false
        inNearestBoundary = false
        inCurrentToCenter = false
    }

    private fun allStateOff() {
        viewModel.setLiveFlagFalse()
    }

    private fun settingUpCurrentToCenterLocationState() {
        viewModel.setCurrentToCenterLiveFlagTrue()
        viewModel.setNearestMarkerLiveFlagFalse()
        viewModel.setNearestBoundaryLiveFlagFalse()
        viewModel.setCalculateAreaLiveFlagFalse()
    }

    private fun settingUpNearestMarkerState() {
        viewModel.setCurrentToCenterLiveFlagFalse()
        viewModel.setNearestMarkerLiveFlagTrue()
        viewModel.setNearestBoundaryLiveFlagFalse()
        viewModel.setCalculateAreaLiveFlagFalse()

    }

    private fun settingUpNearestEdgeState() {
        viewModel.setCurrentToCenterLiveFlagFalse()
        viewModel.setNearestMarkerLiveFlagFalse()
        viewModel.setNearestBoundaryLiveFlagTrue()
        viewModel.setCalculateAreaLiveFlagFalse()
    }

    private fun settingUpCalculateTotalAreaState() {
        viewModel.setCurrentToCenterLiveFlagFalse()
        viewModel.setNearestMarkerLiveFlagFalse()
        viewModel.setNearestBoundaryLiveFlagFalse()
        viewModel.setCalculateAreaLiveFlagTrue()
    }

    @SuppressLint("DefaultLocale")
    private fun findCurrentPointToCenterPoint() {
        // Clear previous markers and lines
        viewModel.setCalculationFlagTrue()
        viewModel.setMarkerFlagFalse()
        removeDottedLineAndMarkers()

        val currentLocation = latLangLiveData.value!!
        val centroid = calculateCentroid(manualMarkerList)

        // Calculate distance using Haversine formula
        val distance = calculateDistance(currentLocation, centroid)


        // Draw Dotted Line
        dottedLine = gMap.addPolyline(
            PolylineOptions()
                .add(currentLocation)
                .add(centroid)
                .color(Color.BLUE)
                .pattern(listOf(Dot(), Gap(20f)))
                .width(10f)
        )

        // Place Marker at Centroid
        nearestPointMarker = gMap.addMarker(
            MarkerOptions().position(centroid).title("Centroid")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        )
        Log.d("disatance" ,"$distance")

        viewModel.isLive.observe(this) { live ->
           Log.d("live","$live")

            if (distance <= 10.00 && live && !serviceStarted) {
                startForegroundService(Intent(this, VibrationService::class.java))
                serviceStarted = true
            }
            if(distance >= 10.00 && !live && serviceStarted) {
            stopService(Intent(this, VibrationService::class.java))
            serviceStarted = false
        }

        }
        // Display Distance on the Line
        val distanceText = String.format("%.2f meters", distance)
        val distanceMarkerIcon = createTextMarker(this, distanceText)


        val area = calculatePolygonArea(manualMarkerList)
        binding.value1.text = "Area: %.2f sq.m".format(area)
        binding.value2.text = distanceText
        binding.secondTitle.text = "Current location to Center"

        distanceMarker = gMap.addMarker(
            MarkerOptions()
                .position(LatLng(
                    (currentLocation.latitude + centroid.latitude) / 2,
                    (currentLocation.longitude + centroid.longitude) / 2
                ))
                .icon(distanceMarkerIcon)
        )

    }

    @SuppressLint("DefaultLocale")
    private fun findNearestMarker() {
        // Clear previous markers and lines
        viewModel.setCalculationFlagTrue()
        viewModel.setMarkerFlagFalse()
        removeDottedLineAndMarkers()

        var shortestDistance = Double.MAX_VALUE
        var nearestPoint: LatLng? = null
        val currentLocation = latLangLiveData.value!!
        // Calculate shortest distance
        for (point in manualMarkerList) {
            val distance = calculateDistance(currentLocation, point)
            if (distance < shortestDistance) {
                shortestDistance = distance
                nearestPoint = point
            }
        }
        viewModel.isLive.observe(this) { live ->
            Log.d("live","$live")

            if (shortestDistance <= 10.00 && live && !serviceStarted) {
                Log.d("intetent","servieces")
                startForegroundService(Intent(this, VibrationService::class.java))
                serviceStarted = true
            }
            if(shortestDistance >= 10.00 && !live && serviceStarted) {
            stopService(Intent(this, VibrationService::class.java))
            serviceStarted = false
        }


        }
        val area = calculatePolygonArea(manualMarkerList)
        binding.value1.text = "Area: %.2f sq.m".format(area)
        binding.value2.text = String.format("%.2f meters", shortestDistance)
        binding.secondTitle.text = "Nearest Marker"

        nearestPoint?.let { point ->
            // Draw marker at nearest point
            nearestPointMarker = gMap.addMarker(
                MarkerOptions().position(point).title("Nearest Point")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
            )

            // Draw dotted line from current location to nearest point
            dottedLine = gMap.addPolyline(
                PolylineOptions()
                    .add(currentLocation, point)
                    .color(Color.BLUE)
                    .pattern(listOf(Dot(), Gap(20f)))
                    .width(10f)
            )

            // Display the distance in meters using a text marker
            val distanceText = String.format("%.2f meters", shortestDistance)
            val distanceMarkerIcon = createTextMarker(this, distanceText)

            distanceMarker = gMap.addMarker(
                MarkerOptions()
                    .position(LatLng(
                        (currentLocation.latitude + point.latitude) / 2,
                        (currentLocation.longitude + point.longitude) / 2
                    ))
                    .icon(distanceMarkerIcon)
            )
        }

    }

    @SuppressLint("DefaultLocale")
    private fun findNearestBoundary() {

        viewModel.setCalculationFlagTrue()
        viewModel.setMarkerFlagFalse()
        removeDottedLineAndMarkers()

        var shortestDistance = Double.MAX_VALUE
        var nearestPoint: LatLng? = null
        val currentLocation = latLangLiveData.value!!

        for (i in manualMarkerList.indices) {
            val startPoint = manualMarkerList[i]
            val endPoint = manualMarkerList[(i + 1) % manualMarkerList.size]
            val pointOnEdge = getNearestPointOnSegment(currentLocation, startPoint, endPoint)
            val distance = calculateDistance(currentLocation, pointOnEdge)

            if (distance < shortestDistance) {
                shortestDistance = distance
                nearestPoint = pointOnEdge
            }
        }
        viewModel.isLive.observe(this) { live ->
            Log.d("live","$live")

            if (shortestDistance <= 10.00 && live && !serviceStarted) {
                startForegroundService(Intent(this, VibrationService::class.java))
                serviceStarted = true
            }
            if(shortestDistance >= 10.00 && !live && serviceStarted) {
                stopService(Intent(this, VibrationService::class.java))
                serviceStarted = false
            }

        }
        val area = calculatePolygonArea(manualMarkerList)
        binding.value1.text = "Area: %.2f sq.m".format(area)
        binding.value2.text = "Area: %.2f sq.m".format(shortestDistance)
        binding.secondTitle.text = "Nearest Boundary"
        // Draw a dotted line from current location to nearest point
        nearestPoint?.let { point ->
            val polylineOptions = PolylineOptions()
                .add(currentLocation)
                .add(point)
                .color(Color.BLUE)
                .pattern(listOf(Dot(), Gap(20f))) // Dotted line pattern
                .width(10f)

            dottedLine = gMap.addPolyline(polylineOptions)

            // Display Distance on Line using a Custom Marker
            val distanceText = String.format("%.2f meters", shortestDistance)
            val distanceMarker = createTextMarker(this, distanceText)

            this@MainActivity.distanceMarker = gMap.addMarker(
                MarkerOptions()
                    .position(LatLng((currentLocation.latitude + point.latitude) / 2,
                        (currentLocation.longitude + point.longitude) / 2))
                    .icon(distanceMarker)
            )
            nearestPointMarker = gMap.addMarker(
                MarkerOptions().position(point)
                    .title("Nearest Point")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
            )

        }
    }

    private fun calculateTheToatalArea() {
        viewModel.setCalculationFlagTrue()
        viewModel.setMarkerFlagFalse()
        removeDottedLineAndMarkers()
        if(manualMarkerList.size>=3) {
            removeDottedLineAndMarkers()
            viewModel.setMarkerFlagFalse()
            val centroid = calculateCentroid(manualMarkerList)
            val area = calculatePolygonArea(manualMarkerList)
            binding.value1.text = "Area: %.2f sq.m".format(area)
            binding.value2.text = ""
            binding.secondTitle.text = ""


            nearestPointMarker = gMap.addMarker(
                MarkerOptions()
                    .position(centroid)
                    .title("Area: %.2f sq.m".format(area))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            )


        }
        else{Toast.makeText(this,"Need to At least 3 Markers",Toast.LENGTH_SHORT).show()}

    }

    private fun addMarkersInTheMap() {
        removeDottedLineAndMarkers()
        viewModel.setCalculationFlagFalse()
        viewModel.setMarkerFlagTrue()
    }

    // Remove Dotted Line and Markers
    private fun removeDottedLineAndMarkers() {
        stopService(Intent(this,VibrationService::class.java))
        dottedLine?.remove()
        nearestPointMarker?.remove()
        distanceMarker?.remove()
        // Clear the references
        dottedLine = null
        nearestPointMarker = null
        distanceMarker = null
    }

    // Setting up on Click on map function listener
    private fun setUpOnClickListener() {
        gMap.setOnMapClickListener { latLang ->
            addManualMarker(latLang)
        }
    }

    /** Check if GPS is enabled */
    private fun isGpsEnabled(): Boolean {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    /** Check and Request Location Permissions */
    private fun checkLocationPermission() {
        if (EasyPermissions.hasPermissions(this, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            if (isGpsEnabled()) {
                requestLocationUpdates()
            } else {
                showEnableGpsDialog()
            }
        } else {
            showPermissionDialog()
        }

    }

    /** Request Location Updates */
    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L) // Every 5 seconds
            .setMinUpdateIntervalMillis(2000L) // Minimum 1 second between updates
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val userLatLng = LatLng(location.latitude, location.longitude)
                    latLangLiveData.value = userLatLng
                    if(animationFlag) {
                        gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 22f))
                        animationFlag = !animationFlag
                    }
                    //setting up for only one action
                    if(viewModel.isLive.value == true){
              Log.d("is this live" ,"${viewModel.isLive.value}")
                        if(viewModel.nearestMarkerLive.value == false && viewModel.currentToCenter.value == false && viewModel.nearestBoundaryLive.value == true && manualMarkerList.size>=3  && viewModel.calculateTotalArea.value==false){
                            findNearestBoundary()

                        }
                        if(viewModel.nearestMarkerLive.value == true && viewModel.currentToCenter.value == false && viewModel.nearestBoundaryLive.value == false && manualMarkerList.size>=3  && viewModel.calculateTotalArea.value==false){
                            findNearestMarker()
                        }
                        if(viewModel.nearestMarkerLive.value == false && viewModel.currentToCenter.value == true && viewModel.nearestBoundaryLive.value == false && manualMarkerList.size>=3  && viewModel.calculateTotalArea.value==false){
                            findCurrentPointToCenterPoint()
                        }

                    }
                }
            }
        }
        gMap.isMyLocationEnabled = true
        gMap.uiSettings.isMyLocationButtonEnabled = false
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    /** Show Permission Dialog */
    private fun showPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Location Permission Required")
            .setMessage("This app needs access to your location to display it on the map.")
            .setPositiveButton("Allow") { _, _ ->
                requestLocationPermission()
            }
            .setNegativeButton("Deny") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    /** Request Location Permission */
    private fun requestLocationPermission() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        EasyPermissions.requestPermissions(
            this,
            "Location access is needed to display your current location on the map.",
            LOCATION_PERMISSION_REQUEST_CODE,
            *permissions.toTypedArray()
        )
    }

    /** Show GPS Enable Dialog */
    private fun showEnableGpsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Enable GPS")
            .setMessage("GPS is required to get your current location.")
            .setPositiveButton("Enable") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }


    /** Handle Permission Request Results */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    /** Handle Granted Permissions */
    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        if (isGpsEnabled()) {
            requestLocationUpdates()
        } else {
            showEnableGpsDialog()
        }
    }

    /** Handle Denied Permissions */
    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {       if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
        // Show settings dialog
        startNotificationDialogSettings()
    }

        Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
    }

    private fun startNotificationDialogSettings() {
                 startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS))
    }

    /** Stop Location Updates when Activity is Destroyed */
    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopService(Intent(this,VibrationService::class.java))
    }
    
    // Add single Marker in the Map
    private fun addManualMarker(latLng: LatLng) {

        // Create a temporary list with the new marker
        val tempMarkers = manualMarkerList.toMutableList()
        tempMarkers.add(latLng)

        // Check if the markers form a valid polygon
        if (tempMarkers.size >= 3 && !isPolygon(tempMarkers)) {
            Toast.makeText(this, "Markers do not form a polygon!", Toast.LENGTH_SHORT).show()
        } else {
            // Add the marker to the map and the list
            val tempMarker = gMap.addMarker(
                MarkerOptions()
                .position(latLng)
                .title("Marker ${manualMarkerList.size}")
                    .draggable(true)
            )
            manualMarkerList.add(latLng)
          markersList.add(tempMarker!!)

            // Draw lines between markers
            drawLinesBetweenMarkers()

            // Draw the polygon if there are at least 3 markers
            if (manualMarkerList.size >= 3) {
                drawPolygon()
            }
        }
    }


    // Function to draw lines between markers
    private fun drawLinesBetweenMarkers() {
        // Clear existing polylines
        gMap.clear()

        // Add all markers to the map
        for (marker in manualMarkerList) {
            gMap.addMarker(MarkerOptions().position(marker).title("Marker ${manualMarkerList.indexOf(marker)}"))
        }

        // Draw lines between markers
        polyline = PolylineOptions()
        for (marker in manualMarkerList) {
            polyline?.add(marker)
        }
        // Close the polygon by connecting the last point to the first point
        if (manualMarkerList.size >= 3) {
            polyline?.add(manualMarkerList[0])
        }
        polyline?.width(5f) // Set the width of the line
        polyline?.color(Color.BLUE) // Set the color of the line
        if (polyline != null) {
            gMap.addPolyline(polyline!!)
        }
    }

    // Function to draw the polygon
    private fun drawPolygon() {

        val polygonOptions = PolygonOptions()
        for (marker in manualMarkerList) {
            polygonOptions.add(marker)
        }
        // Close the polygon by connecting the last point to the first point
        polygonOptions.add(manualMarkerList[0])
        polygonOptions.strokeWidth(5f) // Set the stroke width of the polygon
        polygonOptions.strokeColor(Color.RED) // Set the stroke color
        polygonOptions.fillColor(Color.argb(50, 255, 0, 0)) // Set the fill color with transparency
        gMap.addPolygon(polygonOptions)
    }

    @SuppressLint("PotentialBehaviorOverride")
    fun applyMapStyle() {
        val nightModeFlags = this.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        val styleResId = when (nightModeFlags) {
            Configuration.UI_MODE_NIGHT_YES -> R.raw.dark_map_style
            Configuration.UI_MODE_NIGHT_NO -> R.raw.light_map_style
            else -> R.raw.light_map_style
        }

        try {
            val success = gMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, styleResId))
            if (!success) {
                println("Map style parsing failed.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    fun drawingPolygonOnDragging(newIndex : Int, newLatLng: LatLng) {
        // Check if the markers form a valid polygon


        // Add the marker to the map and the list
        if (manualMarkerList.size >= 3 && !isPolygon(manualMarkerList)) {

            Toast.makeText(this, "Markers do not form a polygon!", Toast.LENGTH_SHORT).show()
            drawLinesBetweenMarkers()

            // Draw the polygon if there are at least 3 markers
            if (manualMarkerList.size >= 3) {
                drawPolygon()
            }
            return
        } else {
            val tempMarker = gMap.addMarker(
                MarkerOptions()
                    .position(newLatLng)
                    .title("Marker ${manualMarkerList.size}")
                    .draggable(true)
            )
            manualMarkerList.removeAt(newIndex)
            markersList.removeAt(newIndex)
            markersList.add(newIndex, tempMarker!!)
            manualMarkerList.add(newIndex, newLatLng)


            // Draw lines between markers
            drawLinesBetweenMarkers()

            // Draw the polygon if there are at least 3 markers
            if (manualMarkerList.size >= 3) {
                drawPolygon()
            }
        }
    }

    @SuppressLint("PotentialBehaviorOverride")
    private fun draggableEvent(){        // Set marker drag listeners
        gMap.setOnMarkerDragListener(

            object : GoogleMap.OnMarkerDragListener {

                override fun onMarkerDragStart(marker: Marker) {
                    // Called when the drag starts


                        marker.title = "Dragging Started!"
                        marker.showInfoWindow()
                        Log.d("postion", "${markersList.indexOf(marker)}")

                }


                override fun onMarkerDrag(marker: Marker) {
                    // Called repeatedly while dragging

                    marker.showInfoWindow()

                }

                override fun onMarkerDragEnd(marker: Marker) {
                    // Called when drag ends
                    val newMarkerIndex = markersList.indexOf(marker)
                    val newLatLng = LatLng(marker.position.latitude, marker.position.longitude)

                    drawingPolygonOnDragging(newMarkerIndex, newLatLng)
                    marker.showInfoWindow()
                }
            })

    }
}