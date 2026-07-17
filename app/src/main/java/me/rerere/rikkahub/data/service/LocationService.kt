@file:Suppress("unused")

package me.rerere.rikkahub.data.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.Geocoder
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.util.Locale

private const val MAX_LAST_KNOWN_LOCATION_AGE_MS = 10 * 60 * 1000L

data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val timestamp: Long = 0L,
    val source: LocationSource = LocationSource.CACHED,
    val forceRefreshRequested: Boolean = false,
    val address: String = "",
    val city: String = "",
    val district: String = "",
    val street: String = "",
    val placeName: String = "",
    val addressProvider: String = "none",
    val poiList: List<PoiInfo> = emptyList()
)

enum class LocationSource {
    FRESH,
    CACHED,
    FALLBACK_CACHED
}

data class PoiInfo(
    val name: String,
    val address: String,
    val distance: Int,
    val type: String,
    val latitude: Double,
    val longitude: Double
)

class LocationService(
    private val context: Context,
    private val amapService: AmapService
) {
    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(
        amapApiKey: String,
        forceRefresh: Boolean = false
    ): Result<LocationInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedLocation = resolveLocation(forceRefresh)
            val location = resolvedLocation?.location

            if (location != null) {
                val amapAddress = if (amapApiKey.isNotBlank()) {
                    val amapCoord = amapService.convertToAmapCoord(location.latitude, location.longitude)
                    val lat = amapCoord?.first ?: location.latitude
                    val lng = amapCoord?.second ?: location.longitude
                    amapService.reverseGeocode(lat, lng).takeIf { it.success }
                } else {
                    null
                }
                val androidAddress = if (amapAddress == null) {
                    reverseGeocodeWithAndroid(location.latitude, location.longitude)
                } else {
                    null
                }
                LocationInfo(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    accuracy = location.accuracy,
                    timestamp = location.time,
                    source = resolvedLocation.source,
                    forceRefreshRequested = forceRefresh,
                    address = amapAddress?.formattedAddress ?: androidAddress?.formattedAddress.orEmpty(),
                    city = amapAddress?.city ?: amapAddress?.province ?: androidAddress?.city.orEmpty(),
                    district = amapAddress?.district ?: androidAddress?.district.orEmpty(),
                    street = amapAddress?.let { address ->
                        buildString {
                            append(address.street ?: "")
                            if (!address.streetNumber.isNullOrBlank()) append(address.streetNumber)
                        }
                    } ?: androidAddress?.street.orEmpty(),
                    placeName = amapAddress?.building
                        ?: amapAddress?.neighborhood
                        ?: androidAddress?.placeName
                        .orEmpty(),
                    addressProvider = when {
                        amapAddress != null -> "amap"
                        androidAddress != null -> "android"
                        else -> "none"
                    },
                )
            } else {
                throw IllegalStateException("无法获取位置信息")
            }
        }
    }

    /**
     * 仅获取坐标，不需要高德API Key，不进行逆地理编码
     */
    @SuppressLint("MissingPermission")
    suspend fun getCoordinatesOnly(forceRefresh: Boolean = false): Result<LocationInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedLocation = resolveLocation(forceRefresh)
            val location = resolvedLocation?.location

            if (location != null) {
                LocationInfo(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    accuracy = location.accuracy,
                    timestamp = location.time,
                    source = resolvedLocation.source,
                    forceRefreshRequested = forceRefresh,
                )
            } else {
                throw IllegalStateException("无法获取位置信息")
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun exploreNearby(
        amapApiKey: String,
        keyword: String = "",
        radius: Int = 1000,
        type: String = "",
        forceRefresh: Boolean = false
    ): Result<List<PoiInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val location = resolveLocation(forceRefresh)?.location

            if (location == null) {
                throw IllegalStateException("无法获取位置信息，请先开启定位")
            }

            amapService.searchNearbyPoi(
                latitude = location.latitude,
                longitude = location.longitude,
                keyword = keyword,
                radius = radius,
                type = type
            ).map { poi ->
                PoiInfo(
                    name = poi.name,
                    address = poi.address,
                    distance = poi.distance,
                    type = poi.type,
                    latitude = poi.latitude,
                    longitude = poi.longitude
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(): Location? {
        val providers = locationManager.getProviders(true)
        return providers.mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
            .filter { it.isFreshEnough() }
            .minWithOrNull(
                compareBy<Location> { it.accuracy }
                    .thenByDescending { it.time }
            )
    }

    @SuppressLint("MissingPermission")
    private suspend fun resolveLocation(forceRefresh: Boolean): ResolvedLocation? {
        if (shouldUseCachedLocation(forceRefresh)) {
            getLastKnownLocation()?.let {
                return ResolvedLocation(it, LocationSource.CACHED)
            }
        }

        requestFreshLocation()?.let {
            return ResolvedLocation(it, LocationSource.FRESH)
        }

        return if (forceRefresh) {
            getLastKnownLocation()?.let { ResolvedLocation(it, LocationSource.FALLBACK_CACHED) }
        } else {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(): Location? {
        return try {
            kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val providers = locationManager.getProviders(true)
                    val preferredProviders = listOf(
                        LocationManager.GPS_PROVIDER,
                        LocationManager.NETWORK_PROVIDER,
                        LocationManager.PASSIVE_PROVIDER
                    ).filter { it in providers }
                    val requestProviders = (preferredProviders.ifEmpty { providers }).ifEmpty {
                        return@suspendCancellableCoroutine cont.resume(null)
                    }

                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            locationManager.removeUpdates(this)
                            if (cont.isActive) cont.resume(location)
                        }

                        override fun onProviderDisabled(provider: String) {}

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

                        override fun onProviderEnabled(provider: String) {}
                    }

                    cont.invokeOnCancellation {
                        locationManager.removeUpdates(listener)
                    }

                    val requested = requestProviders.count { provider ->
                        runCatching {
                            locationManager.requestLocationUpdates(
                                provider,
                                0L,
                                0f,
                                listener
                            )
                        }.isSuccess
                    }
                    if (requested == 0 && cont.isActive) {
                        cont.resume(null)
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocodeWithAndroid(latitude: Double, longitude: Double): LocalAddress? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            val address = Geocoder(context, Locale.getDefault())
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?: return null
            LocalAddress(
                formattedAddress = address.getAddressLine(0).orEmpty(),
                city = address.locality ?: address.adminArea.orEmpty(),
                district = address.subLocality ?: address.subAdminArea.orEmpty(),
                street = listOfNotNull(address.thoroughfare, address.subThoroughfare)
                    .filter(String::isNotBlank)
                    .joinToString(""),
                placeName = address.featureName
                    ?: address.premises
                    ?: address.subLocality
                    .orEmpty(),
            )
        }.getOrNull()
    }
}

private data class LocalAddress(
    val formattedAddress: String,
    val city: String,
    val district: String,
    val street: String,
    val placeName: String,
)

private data class ResolvedLocation(
    val location: Location,
    val source: LocationSource
)

internal fun shouldUseCachedLocation(forceRefresh: Boolean): Boolean = !forceRefresh

internal fun Location.isFreshEnough(nowMillis: Long = System.currentTimeMillis()): Boolean =
    time > 0L && nowMillis - time <= MAX_LAST_KNOWN_LOCATION_AGE_MS
