/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import platform.Foundation.*
import platform.darwin.*
import kotlin.native.concurrent.*
import kotlinx.atomicfu.*

internal actual fun initCurrentThread(): Thread = if (NSThread.isMainThread) mainThread else WorkerThread()

@SharedImmutable
internal val mainThread: MainThread = MainThread()

internal class MainThread : WorkerThread() {
    private val posted = atomic(false)

    init {
        require(NSThread.isMainThread) { "Kotlin runtime must be initialized on the main thread" }
    }

    override fun execute(block: Runnable) {
        super.execute(block)
        // post to main queue if needed
        if (posted.compareAndSet(false, true)) {
            dispatch_async(dispatch_get_main_queue()) {
                posted.value = false // next execute will post a fresh task
                while (worker.processQueue()) { /* process all */ }
            }
        }
    }

    override fun toString(): String = "MainThread"
}
