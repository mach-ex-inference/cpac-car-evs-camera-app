package se.cpacsystems.carevscamera.viewmodel

import android.car.evs.CarEvsManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import se.cpacsystems.carevscamera.R

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StreamViewModel(private val droppedTimeout: Long, private val freezeTimeoutMs: Long) : ViewModel() {
    companion object {
        const val TAG = "StreamViewModel"
    }

    data class IndicatorState(val play: Boolean = true,
                              val stopped: Boolean = true,
                              val frameDropped: Boolean = true,
                              val timeout: Boolean = true)

    private val streamEventFlow = MutableSharedFlow<Int>()
    private val handler = Handler(Looper.getMainLooper())
    private val freezeRunnable = Runnable { onStreamEvent(CarEvsManager.STREAM_EVENT_TIMEOUT) }
    private val droppedRunnable = Runnable { onStreamEvent(CarEvsManager.STREAM_EVENT_FRAME_DROPPED) }

    private val _stateFlow: StateFlow<IndicatorState> by selfReferenced {
        streamEventFlow.flatMapLatest { event ->
            when (event) {
                CarEvsManager.STREAM_EVENT_STREAM_STARTED -> {
                    handler.postDelayed(freezeRunnable, freezeTimeoutMs)
                    handler.postDelayed(droppedRunnable, droppedTimeout)
                    flowOf(_stateFlow.value.copy(play = true, stopped = false, frameDropped = false, timeout = false))
                }
                CarEvsManager.STREAM_EVENT_STREAM_STOPPED -> {
                    handler.removeCallbacks(freezeRunnable)
                    handler.removeCallbacks(droppedRunnable)
                    flowOf(_stateFlow.value.copy(play = false, stopped = true))
                }
                CarEvsManager.STREAM_EVENT_FRAME_DROPPED -> flowOf(_stateFlow.value.copy(frameDropped = true))
                CarEvsManager.STREAM_EVENT_TIMEOUT -> flowOf(_stateFlow.value.copy(timeout = true))
                CarEvsManager.STREAM_EVENT_NONE -> {
                    handler.removeCallbacks(freezeRunnable)
                    handler.removeCallbacks(droppedRunnable)
                    handler.postDelayed(freezeRunnable, freezeTimeoutMs)
                    handler.postDelayed(droppedRunnable, droppedTimeout)
                    flowOf(_stateFlow.value.copy(frameDropped = false, timeout = false))
                }
                else -> throw NotImplementedError("Unknown type of object $event")
            }
            }.catch { Log.d(MainViewModel.TAG, "Exception: $it") }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                IndicatorState()
            )
        }


    val playIndicatorVisibility: Flow<Int> = _stateFlow.mapLatest {
        if (it.play) View.VISIBLE else View.INVISIBLE
    }

    val stoppedIndicatorVisibility: Flow<Int> = _stateFlow.mapLatest {
        if (it.stopped) View.VISIBLE else View.INVISIBLE
    }

    val droppedFrameIndicatorVisibility: Flow<Int> = _stateFlow.mapLatest {
        if (it.frameDropped) View.VISIBLE else View.INVISIBLE
    }

    val timeoutIndicatorVisibility: Flow<Int> = _stateFlow.mapLatest {
        if (it.timeout) View.VISIBLE else View.INVISIBLE
    }

    fun onStreamEvent(event: Int) = viewModelScope
        .launch { streamEventFlow.emit(event) }

    fun onNewFrame() = viewModelScope
        .launch { streamEventFlow.emit(CarEvsManager.STREAM_EVENT_NONE) }

    class StreamViewModelFactory(private val droppedTimeout: Long, private val freezeTimeoutMs: Long): ViewModelProvider.NewInstanceFactory() {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = StreamViewModel(droppedTimeout, freezeTimeoutMs) as T
    }

}