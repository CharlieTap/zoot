@file:OptIn(ExperimentalWebGpuApi::class)

package com.tap.zoot.graphics.webgpu

import android.util.Log
import android.view.Surface
import androidx.webgpu.BackendType
import androidx.webgpu.DeviceLostCallback
import androidx.webgpu.DeviceLostReason
import androidx.webgpu.ExperimentalWebGpuApi
import androidx.webgpu.FeatureLevel
import androidx.webgpu.GPU
import androidx.webgpu.GPUAdapter
import androidx.webgpu.GPUDawnTogglesDescriptor
import androidx.webgpu.GPUDevice
import androidx.webgpu.GPUDeviceDescriptor
import androidx.webgpu.GPURequestAdapterOptions
import androidx.webgpu.GPURequestCallback
import androidx.webgpu.GPUSurfaceConfiguration
import androidx.webgpu.GPUSurfaceDescriptor
import androidx.webgpu.GPUSurfaceSourceAndroidNativeWindow
import androidx.webgpu.PresentMode
import androidx.webgpu.TextureFormat
import androidx.webgpu.TextureUsage
import androidx.webgpu.UncapturedErrorCallback
import androidx.webgpu.helper.Util
import androidx.webgpu.helper.initLibrary
import java.util.concurrent.Executor
import java.util.concurrent.locks.LockSupport

/** Owns the Android WebGPU instance, surface, adapter, device, queue, and configuration. */
internal class AndroidWebGpuContext(
    nativeSurface: Surface,
    width: Int,
    height: Int,
    settings: WebGpuSettings,
) : AutoCloseable {
    val executor = Executor(Runnable::run)
    private val instance =
        run {
            initLibrary()
            GPU.createInstance()
        }
    val surface =
        instance.createSurface(
            GPUSurfaceDescriptor(
                surfaceSourceAndroidNativeWindow = GPUSurfaceSourceAndroidNativeWindow(Util.windowFromSurface(nativeSurface)),
            ),
        )
    private val adapter =
        request<GPUAdapter> { callback ->
            instance.requestAdapter(
                executor,
                GPURequestAdapterOptions(
                    featureLevel = FeatureLevel.Core,
                    backendType = BackendType.Vulkan,
                    compatibleSurface = surface,
                ),
                callback,
            )
        }

    private var lostReason: String? = null
    val device =
        request<GPUDevice> { callback ->
            adapter.requestDevice(
                executor,
                GPUDeviceDescriptor(
                    dawnTogglesDescriptor = GPUDawnTogglesDescriptor(enabledToggles = settings.toggles),
                    deviceLostCallbackExecutor = executor,
                    deviceLostCallback =
                        DeviceLostCallback { _, reason, message ->
                            if (reason != DeviceLostReason.Destroyed) lostReason = message
                            Log.e(LOG_TAG, message)
                        },
                    uncapturedErrorCallbackExecutor = executor,
                    uncapturedErrorCallback = UncapturedErrorCallback { _, _, message -> Log.e(LOG_TAG, message) },
                ),
                callback,
            )
        }
    val queue = device.queue
    val format =
        surface.getCapabilities(adapter).formats.first {
            it == TextureFormat.BGRA8Unorm || it == TextureFormat.RGBA8Unorm
        }
    val description =
        adapter.getInfo().run { "$vendor $architecture $device ($description), Vulkan, androidx.webgpu" }

    init {
        Log.i(LOG_TAG, "$description; validation: ${settings.validation}")
    }

    init {
        surface.configure(
            GPUSurfaceConfiguration(
                device = device,
                format = format,
                usage = TextureUsage.RenderAttachment,
                width = width,
                height = height,
                presentMode = PresentMode.Fifo,
            ),
        )
    }

    fun processEvents() {
        instance.processEvents()
    }

    fun checkDevice() {
        lostReason?.let { throw IllegalStateException("WebGPU device lost: $it") }
    }

    /** Pumps Dawn's events on this thread until [done], backing off once the wait is no longer brief. */
    fun await(done: () -> Boolean) {
        var spins = 0
        while (!done()) {
            processEvents()
            checkDevice()
            if (++spins < SPINS_BEFORE_PARKING) Thread.yield() else LockSupport.parkNanos(PARK_NANOS)
        }
    }

    fun <T> request(block: (GPURequestCallback<T>) -> Unit): T {
        var received: T? = null
        var error: Exception? = null
        var done = false
        block(
            object : GPURequestCallback<T> {
                override fun onResult(result: T) {
                    received = result
                    done = true
                }

                override fun onError(exception: Exception) {
                    error = exception
                    done = true
                }
            },
        )
        await { done }
        error?.let { throw it }
        return checkNotNull(received)
    }

    override fun close() {
        queue.close()
        surface.close()
        device.close()
        adapter.close()
        instance.close()
    }

    private companion object {
        const val LOG_TAG = "Zoot-GPU"
        const val SPINS_BEFORE_PARKING = 64
        const val PARK_NANOS = 50_000L
    }
}
