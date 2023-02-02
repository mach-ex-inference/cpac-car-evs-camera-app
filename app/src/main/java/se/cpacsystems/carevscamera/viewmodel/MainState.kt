package se.cpacsystems.carevscamera.viewmodel

import android.car.Car
import android.car.evs.CarEvsManager
import android.car.evs.CarEvsManager.*
import android.car.evs.CarEvsStatus


data class MainState(val serviceConnectedState: Map<Int, Pair<Car?, CarEvsManager?>> = mapOf(
                         SERVICE_TYPE_REARVIEW to Pair(null, null),
                         SERVICE_TYPE_SURROUNDVIEW to Pair(null, null),
                         SERVICE_TYPE_FRONTVIEW to Pair(null, null)),
                    val carEvsState: Map<Int, CarEvsStatus> = mapOf(
                         SERVICE_TYPE_REARVIEW to CarEvsStatus(SERVICE_TYPE_REARVIEW, SERVICE_STATE_INACTIVE),
                         SERVICE_TYPE_SURROUNDVIEW to CarEvsStatus(SERVICE_TYPE_SURROUNDVIEW, SERVICE_STATE_INACTIVE),
                         SERVICE_TYPE_FRONTVIEW to CarEvsStatus(SERVICE_TYPE_FRONTVIEW, SERVICE_STATE_INACTIVE))) {
}

