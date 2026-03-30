package org.wark.examples.quake1

import org.wark.HostFunction
import org.wark.WarkImports

class QuakeHost(private val frameCallback: FrameCallback? = null) {

    private var palette = ByteArray(768)
    private var frameCount = 0
    var lastSurfacePointer = 0

    fun registerImports(builder: WarkImports.Builder) {
        builder.function("quake:host/system", "error", error())
        builder.function("quake:host/system", "print", print())
        builder.function("quake:host/video", "set-palette", setPalette())
        builder.function("quake:host/video", "update", update())
        builder.function("quake:host/audio", "get-pos", getPos())
        builder.function("quake:host/audio", "submit", submit())
    }

    fun palette(): ByteArray = palette

    fun frameCount(): Int = frameCount

    private fun error(): HostFunction = HostFunction { instance, args ->
        val messageAddress = args[0].toInt()
        val messageLength = args[1].toInt()
        val memory = instance.memory()
        val bytes = memory.readBytes(messageAddress, messageLength)
        val message = String(bytes, Charsets.UTF_8)
        System.err.println("[QUAKE ERROR] $message")
        System.err.flush()
        longArrayOf()
    }

    private fun print(): HostFunction = HostFunction { instance, args ->
        val messageAddress = args[0].toInt()
        val messageLength = args[1].toInt()
        val memory = instance.memory()
        val bytes = memory.readBytes(messageAddress, messageLength)
        val message = String(bytes, Charsets.UTF_8)
        System.out.print(message)
        System.out.flush()
        longArrayOf()
    }

    private fun setPalette(): HostFunction = HostFunction { instance, args ->
        val paletteAddress = args[0].toInt()
        val memory = instance.memory()
        palette = memory.readBytes(paletteAddress, 768)
        longArrayOf()
    }

    private fun update(): HostFunction = HostFunction { instance, args ->
        val framebufferAddress = args[0].toInt()
        val width = args[1].toInt()
        val height = args[2].toInt()
        val stride = args[3].toInt()
        frameCount++
        if (frameCount <= 3 || frameCount % 60 == 0) {
            System.out.println("[QUAKE] Frame $frameCount (${width}x${height}, stride=$stride)")
            System.out.flush()
        }
        frameCallback?.onFrame(instance, framebufferAddress, width, height, stride)
        longArrayOf()
    }

    private var audioPosition = 0

    private fun getPos(): HostFunction = HostFunction { _, _ ->
        longArrayOf(audioPosition.toLong())
    }

    private fun submit(): HostFunction = HostFunction { instance, args ->
        val bufferAddress = args[0].toInt()
        val sampleCount = args[1].toInt()
        audioPosition += sampleCount
        longArrayOf()
    }

    fun interface FrameCallback {
        fun onFrame(
            instance: org.wark.WarkInstance,
            framebufferAddress: Int,
            width: Int,
            height: Int,
            stride: Int,
        )
    }
}
