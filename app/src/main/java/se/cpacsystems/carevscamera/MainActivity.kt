package se.cpacsystems.carevscamera

import android.car.Car
import android.car.Car.CarServiceLifecycleListener
import android.car.evs.CarEvsBufferDescriptor
import android.car.evs.CarEvsManager
import android.car.evs.CarEvsManager.*
import android.content.res.Resources
import android.graphics.PixelFormat
import android.graphics.PorterDuff.Mode.SRC_IN
import android.hardware.HardwareBuffer
import android.os.*
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import se.cpacsystems.carevscamera.GLES20CarEvsCameraPreviewRenderer.FrameProvider
import se.cpacsystems.carevscamera.viewmodel.StreamViewModel
import se.cpacsystems.carevscamera.viewmodel.MainViewModel
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"

        private const val MAX_FRAME_LAG = 3

        /**
         * Number of frames per second we expect from all cameras.
         */
        private const val EXPECTED_FPS = 30

        /**
         * Time from last frame until we show a "dropped" warning indication.
         */
        private const val FRAME_DROPPED_TIMEOUT_MS = (1000L/EXPECTED_FPS) * 2

        /**
         * Time from last frame until we show a "freeze" warning indication.
         */
        private const val STREAM_FREEZE_TIMEOUT_MS = 2000L

        /**
         * GLES20CarEvsCameraPreviewRenderer::FrameProvider::returnFrame implementation.
         */
        @VisibleForTesting
        fun returnFrame(frame: HardwareBuffer?, buffer: ArrayList<CarEvsBufferDescriptor>, manager: CarEvsManager?, view: CarEvsCameraGLSurfaceView?) {
            frame?.let {
                manager?.let {
                    val desc = synchronized(buffer) {
                        buffer.removeAt(0)
                    }
                    manager.returnFrameBuffer(desc)
                    synchronized(buffer) {
                        if(buffer.isNotEmpty()) {
                            // More frames to render in buffer
                            view?.requestRender()
                        }
                    }
                }
            }
        }

        /**
         * GLES20CarEvsCameraPreviewRenderer::FrameProvider::getNewFrame implementation.
         */
        @VisibleForTesting
        fun getNewFrame(buffer: ArrayList<CarEvsBufferDescriptor>): HardwareBuffer? {
            synchronized(buffer) {
                if (buffer.isEmpty()) {
                    return null
                }
                return buffer.getOrNull(0)?.hardwareBuffer
            }
        }

        /**
         * CarServiceLifecycleListener::onLifecycleChanged implementation.
         *
         * Upon connection to the Car service it creates a CarEvsManager and post a signal
         * to the handler using provided message.
         */
        @VisibleForTesting
        fun onLifecycleChanged(car: Car, ready: Boolean, viewModel: MainViewModel, type: Int, executor: Executor) {
            if (!ready) {
                Log.d(TAG, "Disconnected from the Car Service")
                viewModel.onServiceEvent(type, null, null)
            } else {
                Log.d(TAG, "Connected to the Car Service")
                val manager = (car.getCarManager(Car.CAR_EVS_SERVICE) as CarEvsManager)
                    .apply { setStatusListener(executor) { viewModel.onEvsEvent(it) } }
                viewModel.onServiceEvent(type, car, manager)
            }
        }

        /**
         * CarEvsStreamCallback::onStreamEvent implementation.
         *
         * If the stream is stopped we release all pending frames in our buffer.
         */
        @VisibleForTesting
        fun onStreamEvent(event: Int, viewModel: StreamViewModel, buffer: ArrayList<CarEvsBufferDescriptor>, manager: CarEvsManager?) {
            viewModel.onStreamEvent(event)
            if (event == STREAM_EVENT_STREAM_STOPPED) {
                synchronized(buffer) {
                    for (frame in buffer) {
                        frame.hardwareBuffer.close()
                        manager?.returnFrameBuffer(frame)
                    }
                }
            }
        }

        /**
         * CarEvsStreamCallback::onNewFrame implementation.
         *
         * If there's no attached view OR if the buffer is full
         *      We immediately release the buffer and return the frame
         * Else
         *      We add a new frame to our buffer.
         *
         * Then we request the view to render the frame.
         */
        @VisibleForTesting
        fun onNewFrame(frame: CarEvsBufferDescriptor, viewModel: StreamViewModel, buffer: ArrayList<CarEvsBufferDescriptor>, manager: CarEvsManager?, view: CarEvsCameraGLSurfaceView?) {
            viewModel.onNewFrame()
            synchronized(buffer) {
                if (buffer.size >= MAX_FRAME_LAG || view == null) {
                    frame.hardwareBuffer.close()
                    manager?.returnFrameBuffer(frame)
                } else {
                    buffer.add(frame)
                }
            }
            view?.requestRender()
        }

        /**
         * Request to stream a specific type.
         */
        @VisibleForTesting
        fun requestStartStream(type: Int, manager: CarEvsManager?, token: IBinder?, callbackExecutor: ExecutorService?, carEvsStreamCallback: CarEvsStreamCallback, view: CarEvsCameraGLSurfaceView?, resources: Resources) {
            manager?.let {
                callbackExecutor?.let {
                    view?.let {
                        when(manager.currentStatus.state) {
                            SERVICE_STATE_UNAVAILABLE -> Log.w(TAG, "Service type $type is unavailable!")
                            SERVICE_STATE_ACTIVE, SERVICE_STATE_INACTIVE, SERVICE_STATE_REQUESTED -> {
                                manager.startVideoStream(
                                    type,
                                    token,
                                    callbackExecutor,
                                    carEvsStreamCallback).let { status ->
                                    when (status) {
                                        ERROR_UNAVAILABLE, ERROR_BUSY -> {
                                            view.renderDrawable(resources.getDrawable(R.drawable.ic_camera_off_24, null))
                                        }
                                    }
                                }
                            }
                            else -> throw NotImplementedError()
                        }
                    } ?: throw java.lang.IllegalArgumentException("view is null")
                } ?: throw java.lang.IllegalArgumentException("callbackExecutor is null")
            } ?: throw java.lang.IllegalArgumentException("manager is null")
        }
    }

    private var evsGLSurfaceViewRear: CarEvsCameraGLSurfaceView? = null
    private var evsGLSurfaceViewFront: CarEvsCameraGLSurfaceView? = null
    private val mainViewModel by viewModels<MainViewModel>()
    private val frontviewStreamStreamViewModel: StreamViewModel by viewModels { StreamViewModel.StreamViewModelFactory(FRAME_DROPPED_TIMEOUT_MS, STREAM_FREEZE_TIMEOUT_MS) }
    private val rearviewStreamStreamViewModel: StreamViewModel by viewModels { StreamViewModel.StreamViewModelFactory(FRAME_DROPPED_TIMEOUT_MS, STREAM_FREEZE_TIMEOUT_MS) }
    private val bufferQueueRear = ArrayList<CarEvsBufferDescriptor>(MAX_FRAME_LAG)
    private val bufferQueueFront = ArrayList<CarEvsBufferDescriptor>(MAX_FRAME_LAG)
    private val callbackExecutor = Executors.newFixedThreadPool(1)
    private val frameProviderRear = object : FrameProvider {
        override fun getNewFrame(): HardwareBuffer? = getNewFrame(bufferQueueRear)
        override fun returnFrame(frame: HardwareBuffer?) = returnFrame(frame, bufferQueueRear, carEvsManagerRear, evsGLSurfaceViewRear)
    }
    private val frameProviderFront = object : FrameProvider {
        override fun getNewFrame(): HardwareBuffer? = getNewFrame(bufferQueueFront)
        override fun returnFrame(frame: HardwareBuffer?) = returnFrame(frame, bufferQueueFront, carEvsManagerFront, evsGLSurfaceViewFront)
    }
    private val carServiceLifecycleListenerRear = CarServiceLifecycleListener { car, ready -> onLifecycleChanged(car, ready, mainViewModel, SERVICE_TYPE_REARVIEW, callbackExecutor) }
    private val carServiceLifecycleListenerFront = CarServiceLifecycleListener { car, ready -> onLifecycleChanged(car, ready, mainViewModel, SERVICE_TYPE_FRONTVIEW, callbackExecutor) }
    private val carEvsStreamCallbackRear = object : CarEvsStreamCallback {
        override fun onStreamEvent(event: Int) = onStreamEvent(event, rearviewStreamStreamViewModel, bufferQueueRear, carEvsManagerRear)
        override fun onNewFrame(frame: CarEvsBufferDescriptor) = onNewFrame(frame, rearviewStreamStreamViewModel, bufferQueueRear, carEvsManagerRear, evsGLSurfaceViewRear)
    }
    private val carEvsStreamCallbackFront:CarEvsStreamCallback = object : CarEvsStreamCallback {
        override fun onStreamEvent(event: Int) = onStreamEvent(event, frontviewStreamStreamViewModel, bufferQueueFront, carEvsManagerFront)
        override fun onNewFrame(frame: CarEvsBufferDescriptor) = onNewFrame(frame, frontviewStreamStreamViewModel, bufferQueueFront, carEvsManagerFront, evsGLSurfaceViewFront)
    }

    private var carRear: Car? = null
    private var carFront: Car? = null
    private var carEvsManagerRear: CarEvsManager? = null
    private var carEvsManagerFront: CarEvsManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        (LayoutInflater.from(this).inflate(R.layout.main,  /* root= */null) as ConstraintLayout?)?.apply {
            Log.i(TAG, "Setting up views")

            findViewById<LinearLayout>(R.id.evs_preview_container_front)?.let { previewContainer ->
                evsGLSurfaceViewFront =
                    CarEvsCameraGLSurfaceView(application, frameProviderFront).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1.0f
                        )
                    }
                previewContainer.addView(evsGLSurfaceViewFront, 0)
            }

            findViewById<LinearLayout>(R.id.evs_preview_container_rear)?.let { previewContainer ->
                evsGLSurfaceViewRear =
                    CarEvsCameraGLSurfaceView(application, frameProviderRear).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1.0f
                        )
                    }
                previewContainer.addView(evsGLSurfaceViewRear, 0)
            }

            findViewById<ConstraintLayout>(R.id.rear_camera_stream_indicators)?.let { indicators ->
                indicators.findViewById<ImageView>(R.id.playIndicator)?.apply {
                    rearviewStreamStreamViewModel.playIndicatorTint
                        .onEach { setColorFilter(ContextCompat.getColor(context, it), SRC_IN) }
                        .launchIn(MainScope())
                    }
                indicators.findViewById<ImageView>(R.id.stopIndicator)?.apply {
                    rearviewStreamStreamViewModel.stoppedIndicatorTint
                        .onEach { setColorFilter(ContextCompat.getColor(context, it), SRC_IN)  }
                        .launchIn(MainScope())
                }
                indicators.findViewById<ImageView>(R.id.frameDroppedIndicator)?.apply {
                    rearviewStreamStreamViewModel.droppedFrameIndicatorTint
                        .onEach { setColorFilter(ContextCompat.getColor(context, it), SRC_IN)  }
                        .launchIn(MainScope())
                }
                indicators.findViewById<ImageView>(R.id.timeoutIndicator)?.apply {
                    rearviewStreamStreamViewModel.timeoutIndicatorTint
                        .onEach { setColorFilter(ContextCompat.getColor(context, it), SRC_IN)  }
                        .launchIn(MainScope())
                }
            }

            findViewById<ConstraintLayout>(R.id.front_camera_stream_indicators)?.let { indicators ->
                indicators.findViewById<ImageView>(R.id.playIndicator)?.apply {
                    frontviewStreamStreamViewModel.playIndicatorTint
                        .onEach { setColorFilter(ContextCompat.getColor(context, it), SRC_IN) }
                        .launchIn(MainScope())
                }
                indicators.findViewById<ImageView>(R.id.stopIndicator)?.apply {
                    frontviewStreamStreamViewModel.stoppedIndicatorTint
                        .onEach { setColorFilter(ContextCompat.getColor(context, it), SRC_IN) }
                        .launchIn(MainScope())
                }
                indicators.findViewById<ImageView>(R.id.frameDroppedIndicator)?.apply {
                    frontviewStreamStreamViewModel.droppedFrameIndicatorTint
                        .onEach { setColorFilter(ContextCompat.getColor(context, it), SRC_IN) }
                        .launchIn(MainScope())
                }
                indicators.findViewById<ImageView>(R.id.timeoutIndicator)?.apply {
                    frontviewStreamStreamViewModel.timeoutIndicatorTint
                        .onEach { setColorFilter(ContextCompat.getColor(context, it), SRC_IN) }
                        .launchIn(MainScope())
                }
            }

            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                0,
                0,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.LEFT or Gravity.TOP
            }.let {
                setContentView(this, it)
            }
        }

        mainViewModel.requestStartStreamRear
            .onEach {
                carRear = it.first
                carEvsManagerRear = it.second
                if (carRear != null && carEvsManagerRear != null) {
                    requestStartStream(SERVICE_TYPE_REARVIEW, carEvsManagerRear, null, callbackExecutor, carEvsStreamCallbackRear, evsGLSurfaceViewRear, resources)
                }
            }
            .launchIn( MainScope() )
        Car.createCar(applicationContext, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, carServiceLifecycleListenerRear)

        mainViewModel.requestStartStreamFront
            .onEach {
                carFront = it.first
                carEvsManagerFront = it.second
                if (carFront != null && carEvsManagerFront != null) {
                    requestStartStream(SERVICE_TYPE_FRONTVIEW, carEvsManagerFront, null, callbackExecutor, carEvsStreamCallbackFront, evsGLSurfaceViewFront, resources) }
                }
            .launchIn( MainScope() )
        Car.createCar(applicationContext, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, carServiceLifecycleListenerFront)
    }

    override fun onResume() {
        super.onResume()
        // https://developer.android.com/reference/android/opengl/GLSurfaceView.html#activity-life-cycle
        evsGLSurfaceViewRear?.onResume()
        evsGLSurfaceViewFront?.onResume()
    }

    override fun onPause() {
        super.onPause()
        // https://developer.android.com/reference/android/opengl/GLSurfaceView.html#activity-life-cycle
        evsGLSurfaceViewRear?.onPause()
        evsGLSurfaceViewFront?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        carEvsManagerRear?.apply{
            stopVideoStream()
            clearStatusListener()
        }
        carEvsManagerFront?.apply {
            stopVideoStream()
            clearStatusListener()
        }

        carFront?.disconnect()
        carRear?.disconnect()
        mainViewModel.onServiceEvent(SERVICE_TYPE_REARVIEW,null, null)
        mainViewModel.onServiceEvent(SERVICE_TYPE_REARVIEW,null, null)

        carEvsManagerRear = null
        carEvsManagerFront = null
        carFront = null
        carRear = null
    }

   override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        return if (id == R.id.action_settings) {
            true
        } else super.onOptionsItemSelected(item)
    }
}