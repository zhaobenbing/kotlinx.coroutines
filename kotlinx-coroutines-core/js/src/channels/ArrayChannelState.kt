/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.channels

import kotlin.math.*

internal actual class ArrayChannelState actual constructor(initialBufferSize: Int) {
    private var buffer: Array<Any?> = arrayOfNulls<Any?>(initialBufferSize)

    actual var head = 0
    actual var size = 0
    actual val bufferSize: Int get() = buffer.size

    actual fun getBufferAt(index: Int): Any? =
        buffer[index]

    actual fun setBufferAt(index: Int, value: Any?) {
        buffer[index] = value
    }

    actual fun ensureCapacity(currentSize: Int, capacity: Int) {
        if (currentSize < buffer.size) return
        val newSize = min(buffer.size * 2, capacity)
        val newBuffer = arrayOfNulls<Any?>(newSize)
        for (i in 0 until currentSize) {
            newBuffer[i] = buffer[(head + i) % buffer.size]
        }
        buffer = newBuffer
        head = 0
    }

    actual inline fun <T> withLock(block: ArrayChannelState.() -> T): T =
        block()
}
