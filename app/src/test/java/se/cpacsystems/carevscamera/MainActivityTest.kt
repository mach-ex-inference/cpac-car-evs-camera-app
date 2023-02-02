package se.cpacsystems.carevscamera

import android.car.Car
import android.car.evs.CarEvsBufferDescriptor
import android.car.evs.CarEvsManager
import android.car.evs.CarEvsManager.STREAM_EVENT_STREAM_STARTED
import android.car.evs.CarEvsManager.STREAM_EVENT_STREAM_STOPPED
import android.car.evs.CarEvsStatus
import android.content.res.Resources
import android.hardware.HardwareBuffer
import android.os.Handler
import junit.framework.TestCase
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import se.cpacsystems.carevscamera.viewmodel.MainViewModel
import se.cpacsystems.carevscamera.viewmodel.StreamViewModel
import java.util.concurrent.ExecutorService

class MainActivityTest : TestCase() {

    @Mock lateinit var frame: HardwareBuffer
    @Mock lateinit var bufferDescriptor: CarEvsBufferDescriptor
    @Mock lateinit var buffer: ArrayList<CarEvsBufferDescriptor>
    @Mock lateinit var manager: CarEvsManager
    @Mock lateinit var carEvsStatus: CarEvsStatus
    @Mock lateinit var view: CarEvsCameraGLSurfaceView
    @Mock lateinit var car: Car
    @Mock lateinit var viewModel: MainViewModel
    @Mock lateinit var executor: ExecutorService
    @Mock lateinit var handler: Handler
    @Mock lateinit var streamViewModel: StreamViewModel
    @Mock lateinit var streamCallback : CarEvsManager.CarEvsStreamCallback
    @Mock lateinit var resources: Resources

    @Before
    override fun setUp() {
        super.setUp()
        MockitoAnnotations.openMocks(this)
        whenever(bufferDescriptor.hardwareBuffer).thenReturn(frame)
        whenever(manager.currentStatus).thenReturn(carEvsStatus)
    }

    @Test
    fun testReturnFrame_oneFrameInBuffer() {
        whenever(buffer.removeAt(eq(0))).thenReturn(bufferDescriptor)
        whenever(buffer.isEmpty()).thenReturn(true)

        MainActivity.returnFrame(frame, buffer, manager, view)

        verify(buffer).removeAt(eq(0))
        verify(buffer).isNotEmpty()
        verify(manager).returnFrameBuffer(bufferDescriptor)
        verifyNoMoreInteractions(buffer, manager, view)
    }

    @Test
    fun testReturnFrame_twpFrameInBuffer() {
        whenever(buffer.removeAt(eq(0))).thenReturn(bufferDescriptor)
        whenever(buffer.isEmpty()).thenReturn(false)

        MainActivity.returnFrame(frame, buffer, manager, view)

        verify(buffer).removeAt(eq(0))
        verify(buffer).isNotEmpty()
        verify(manager).returnFrameBuffer(bufferDescriptor)
        verify(view).requestRender()
        verifyNoMoreInteractions(buffer, manager, view)
    }

    @Test
    fun testGetNewFrame_emptyBuffer() {
        assertNull(MainActivity.getNewFrame(buffer))
    }

    @Test
    fun testGetNewFrame_oneFrameInBuffer() {
        buffer = arrayListOf(bufferDescriptor)

        assertEquals(frame, MainActivity.getNewFrame(buffer))
    }

    @Test
    fun testOnLifecycleChanged_connected() {
        whenever(car.getCarManager(eq(Car.CAR_EVS_SERVICE))).thenReturn(manager)

        MainActivity.onLifecycleChanged(car, true, viewModel, CarEvsManager.SERVICE_TYPE_REARVIEW, executor)

        verify(car).getCarManager(eq(Car.CAR_EVS_SERVICE))
        verify(viewModel).onServiceEvent(CarEvsManager.SERVICE_TYPE_REARVIEW, car, manager)
        verifyNoMoreInteractions(car, viewModel, executor, handler)
    }

    @Test
    fun testOnLifecycleChanged_disconnected() {
        MainActivity.onLifecycleChanged(car, false, viewModel, CarEvsManager.SERVICE_TYPE_REARVIEW, executor)

        verify(viewModel).onServiceEvent(CarEvsManager.SERVICE_TYPE_REARVIEW, null, null)
        verifyNoMoreInteractions(car, viewModel, executor, handler)
    }

    @Test
    fun testOnStreamEvent_onEventStreamStarted() {
        MainActivity.onStreamEvent(STREAM_EVENT_STREAM_STARTED, streamViewModel, buffer, manager)

        verify(streamViewModel).onStreamEvent(eq(STREAM_EVENT_STREAM_STARTED))
    }

    @Test
    fun testOnStreamEvent_onEventStreamStoppedWithOneFrameInBuffer() {
        buffer = arrayListOf(bufferDescriptor)

        MainActivity.onStreamEvent(STREAM_EVENT_STREAM_STOPPED, streamViewModel, buffer, manager)

        verify(streamViewModel).onStreamEvent(eq(STREAM_EVENT_STREAM_STOPPED))
        verify(frame).close()
    }

    @Test
    fun testRequestStartStream() {
        MainActivity.requestStartStream(CarEvsManager.SERVICE_TYPE_REARVIEW, manager, null, executor, streamCallback, view, resources)
    }
}