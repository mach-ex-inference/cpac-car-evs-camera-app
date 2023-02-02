package se.cpacsystems.carevscamera.viewmodel

import android.car.Car
import android.car.evs.CarEvsManager
import android.car.evs.CarEvsManager.SERVICE_TYPE_FRONTVIEW
import android.car.evs.CarEvsManager.SERVICE_TYPE_REARVIEW
import android.car.evs.CarEvsStatus
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import se.cpacsystems.carevscamera.viewmodel.MainState.*
import se.cpacsystems.carevscamera.viewmodel.MainViewModel.StreamState.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel : ViewModel() {

    companion object {
        const val TAG = "MainViewModel"
    }

    data class ServiceEvent(val type: Int, val car: Car?, val manager: CarEvsManager?)

    enum class StreamType(val value: Int) {
        SERVICE_TYPE_REARVIEW (CarEvsManager.SERVICE_TYPE_REARVIEW),
        SERVICE_TYPE_SURROUNDVIEW (CarEvsManager.SERVICE_TYPE_SURROUNDVIEW),
        SERVICE_TYPE_FRONTVIEW (CarEvsManager.SERVICE_TYPE_FRONTVIEW),
    }

    enum class StreamState(val value: Int) {
        UNAVAILABLE (CarEvsManager.SERVICE_STATE_UNAVAILABLE),
        INACTIVE (CarEvsManager.SERVICE_STATE_INACTIVE),
        REQUESTED (CarEvsManager.SERVICE_STATE_REQUESTED),
        ACTIVE (CarEvsManager.SERVICE_STATE_ACTIVE),
    }

    private val serviceEventFlow = MutableSharedFlow<ServiceEvent>()
    private val evsEventFlow = MutableSharedFlow<CarEvsStatus>()

    private val _stateFlow: StateFlow<MainState> by selfReferenced {
        merge(serviceEventFlow, evsEventFlow)
                .flatMapLatest { event ->
                    when (event) {
                        is ServiceEvent -> {
                            _stateFlow.value.serviceConnectedState.let {
                                it.toMutableMap().let {
                                    it[event.type] = Pair(event.car, event.manager)
                                    flowOf(_stateFlow.value.copy(serviceConnectedState = it))
                                }
                            }
                        }
                        is CarEvsStatus -> {
                            _stateFlow.value.carEvsState.let {
                                it.toMutableMap().let {
                                    it[event.serviceType] = event
                                    flowOf(_stateFlow.value.copy(carEvsState = it))
                                }
                            }
                        }
                        else -> throw NotImplementedError("Unknown type of object $event")
                    }
                }
                .onEach {
                    Log.d(TAG, "Main state:")
                    it.serviceConnectedState.forEach {
                        Log.d(TAG, "        Service: ${StreamType.values()[it.key]} , ${it.value}")
                    }
                    it.carEvsState.forEach {
                        Log.d(TAG, "   CarEvsStatus: ${StreamType.values()[it.value.serviceType]} , ${StreamState.values()[it.value.state]}")
                    }
                }
                .catch { Log.d(TAG, "Exception: $it") }
                .stateIn(
                    viewModelScope,
                    SharingStarted.Eagerly,
                    MainState()
                )
        }

    val requestStartStreamRear: Flow<Pair<Car?, CarEvsManager?>> = _stateFlow.mapLatest {
        it.serviceConnectedState[SERVICE_TYPE_REARVIEW] ?: Pair(null, null)
    }

    val requestStartStreamFront: Flow<Pair<Car?, CarEvsManager?>> = _stateFlow.mapLatest {
        it.serviceConnectedState[SERVICE_TYPE_FRONTVIEW] ?: Pair(null, null)
    }

    fun onServiceEvent(type: Int, car: Car?, manager: CarEvsManager?) = viewModelScope
        .launch { serviceEventFlow.emit(ServiceEvent(type, car, manager)) }

    fun onEvsEvent(event: CarEvsStatus) = viewModelScope
        .launch { evsEventFlow.emit(event) }

}