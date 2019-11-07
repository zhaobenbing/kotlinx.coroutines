/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import platform.Foundation.*
import platform.darwin.*
import kotlin.native.concurrent.*
import kotlinx.atomicfu.*

internal actual fun initCurrentThread(): Thread =
    /* if (NSThread.isMainThread) mainThread  else */ WorkerThread(Worker.current)

@SharedImmutable
internal val mainThread: MainThread = MainThread()

private const val NOT_PARKED = 0
private const val PARKED = 1
private const val SIGNALLED = 2

internal class MainThread : Thread() {
    private val semaphore = dispatch_semaphore_create(0)
    private val _parked = atomic(NOT_PARKED)

    private var parked: Int
        get() = _parked.value
        set(value) { _parked.value = value }
    
    override fun execute(block: Runnable) {
        dispatch_async(dispatch_get_main_queue()) {
            block.run()
            if (parked == PARKED) {
                parked = SIGNALLED
                dispatch_semaphore_signal(semaphore)
            }
        }
    }

    override fun parkNanos(timeout: Long) {
        if (parked == NOT_PARKED) parked = PARKED
        val time = dispatch_time(DISPATCH_TIME_NOW, timeout)
        val result = dispatch_semaphore_wait(semaphore, time)
        if (result == 0L || parked == PARKED) parked = NOT_PARKED
    }

    override fun toString(): String = "MainThread"
}
