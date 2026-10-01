package com.tap.zoot.graphics.webgpu

import android.util.Log
import android.view.Surface
import androidx.webgpu.BackendType
import androidx.webgpu.DeviceLostCallback
import androidx.webgpu.FeatureLevel
import androidx.webgpu.GPU
import androidx.webgpu.GPUAdapter
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
import java.util.concurrent.atomic.AtomicBoolean

/** Owns the Android WebGPU instance, surface, adapter, device, queue, and configuration. */
internal class AndroidWebGpuContext(
    nativeSurface: Surface,
    width: Int,
    height: Int,
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
    val device =
        request<GPUDevice> { callback ->
            adapter.requestDevice(
                executor,
                GPUDeviceDescriptor(
                    deviceLostCallbackExecutor = executor,
                    deviceLostCallback = DeviceLostCallback { _, _, message -> Log.e(LOG_TAG, message) },
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

    fun <T> request(block: (GPURequestCallback<T>) -> Unit): T {
        var received: T? = null
        var error: Exception? = null
        val done = AtomicBoolean(false)
        block(
            object : GPURequestCallback<T> {
                override fun onResult(result: T) {
                    received = result
                    done.set(true)
                }

                override fun onError(exception: Exception) {
                    error = exception
                    done.set(true)
                }
            },
        )
        while (!done.get()) {
            instance.processEvents()
            Thread.yield()
        }
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
    }
}
