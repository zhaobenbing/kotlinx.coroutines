/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.channels

internal expect class ArrayChannelState(initialBufferSize: Int) {
    var head: Int
    var size: Int // Invariant: size <= capacity
    val bufferSize: Int

    fun getBufferAt(index: Int): Any?
    fun setBufferAt(index: Int, value: Any?)
    fun ensureCapacity(currentSize: Int, capacity: Int)

    inline fun <T> withLock(block: ArrayChannelState.() -> T): T
}
