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
    private var service = false
    private var serviceStarted = false



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
            markerState()
            calculationState()
            setUpIsLive()
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

    private fun setAllStatesFalse() {
        viewModel.setNearestMarkerLiveFlagFalse()
        viewModel.setNearestBoundaryLiveFlagFalse()
        viewModel.setCurrentToCenterLiveFlagFalse()
    }

    private fun setUpIsLive() {
        binding.liveButton.isChecked = false
        //Live fetching
        binding.liveButton.setOnCheckedChangeListener{ _, isChecked ->
            if(isChecked){

                viewModel.setLiveFlagTrue()


            }else{

                viewModel.setLiveFlagFalse()

            }
            Log.d("livedata","${viewModel.isLive.value}")
        }

    }

    private fun calculationState() {
        //calculation state
        viewModel.calculationLiveData.observe(this){ flag->
            binding.let{
                it.cancelCard.visibility = if(flag) View.VISIBLE else View.GONE
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
                    addMarkersInTheMap()
                    true
                }
                R.id.calculate_total_area ->{
                    calculateTheToatalArea()
                    true
                }
                R.id.find_nearestEdge -> {
                    if(manualMarkerList.size<3) {
                        Toast.makeText(this, "Need at least Markers",Toast.LENGTH_SHORT).show()
                        return@setOnMenuItemClickListener true
                    }
                    findNearestBoundary()
                  viewModel.isLive.observe(this){live ->
                      if(live){
                          viewModel.setCurrentToCenterLiveFlagFalse()
                          viewModel.setNearestMarkerLiveFlagFalse()
                          viewModel.setNearestBoundaryLiveFlagTrue()
                      }
                  }


                    true
                }
                R.id.find_nearest_marker ->{
                    if(manualMarkerList.size<3) {
                        Toast.makeText(this, "Need at least Markers",Toast.LENGTH_SHORT).show()
                        return@setOnMenuItemClickListener true
                    }
                    findNearestMarker()
                    viewModel.isLive.observe(this) { live ->
                        if (live) {
                            viewModel.setCurrentToCenterLiveFlagFalse()
                            viewModel.setNearestMarkerLiveFlagTrue()
                            viewModel.setNearestBoundaryLiveFlagFalse()
                        }
                    }
                    true
                }
                R.id.current_to_center_point ->{
                    if(manualMarkerList.size<3) {
                        Toast.makeText(this, "Need at least Markers",Toast.LENGTH_SHORT).show()
                        return@setOnMenuItemClickListener true
                    }
                    findCurrentPointToCenterPoint()
                    viewModel.isLive.observe(this) { live ->
                        if (live) {
                            viewModel.setCurrentToCenterLiveFlagTrue()
                            viewModel.setNearestMarkerLiveFlagFalse()
                            viewModel.setNearestBoundaryLiveFlagFalse()
                        }
                    }
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

            if (distance <= 5.00 && live && !serviceStarted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(Intent(this, VibrationService::class.java))
                } else {
                    startService(Intent(this, VibrationService::class.java))
                }
                serviceStarted = true
            } else {
                stopService(Intent(this, VibrationService::class.java))
                serviceStarted = false
            }

        }


        // Display Distance on the Line
        val distanceText = String.format("%.2f meters", distance)
        val distanceMarkerIcon = createTextMarker(this, distanceText)

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

            if (shortestDistance <= 5.00 && live && !serviceStarted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(Intent(this, VibrationService::class.java))
                } else {
                    startService(Intent(this, VibrationService::class.java))
                }
                serviceStarted = true
            } else {
                stopService(Intent(this, VibrationService::class.java))
                serviceStarted = false
            }

        }
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

            if (shortestDistance <= 5.00 && live && !serviceStarted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(Intent(this, VibrationService::class.java))
                } else {
                    startService(Intent(this, VibrationService::class.java))
                }
                serviceStarted = true
            } else {
                stopService(Intent(this, VibrationService::class.java))
                serviceStarted = false
            }

        }

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
        removeDottedLineAndMarkers()
        if(manualMarkerList.size>=3) {
            removeDottedLineAndMarkers()
            viewModel.setMarkerFlagFalse()
            val area = calculatePolygonArea(manualMarkerList)
            val centroid = calculateCentroid(manualMarkerList)


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
        dottedLine?.remove()
        nearestPointMarker?.remove()
        distanceMarker?.remove()
        // Clear the references
        dottedLine = null
        nearestPointMarker = null
        distanceMarker = null
        stopService(Intent(this,VibrationService::class.java))
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
                        if(viewModel.nearestMarkerLive.value == false && viewModel.currentToCenter.value == false && viewModel.nearestBoundaryLive.value == true && manualMarkerList.size>=3){
                            findNearestBoundary()

                        }
                        if(viewModel.nearestMarkerLive.value == true && viewModel.currentToCenter.value == false && viewModel.nearestBoundaryLive.value == false && manualMarkerList.size>=3){
                            findNearestMarker()
                        }
                        if(viewModel.nearestMarkerLive.value == false && viewModel.currentToCenter.value == true && viewModel.nearestBoundaryLive.value == false && manualMarkerList.size>=3){
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
    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
    }

    /** Stop Location Updates when Activity is Destroyed */
    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
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
            val tempMarker = gMap.addMarker(MarkerOptions().position(latLng).title("Marker ${manualMarkerList.size}"))
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
}