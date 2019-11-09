/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import platform.darwin.*
import kotlin.native.concurrent.*
import kotlinx.atomicfu.*

/**
 * Initializes the main thread. Must be called from the main thread if the application's interaction
 * with Kotlin runtime and coroutines API otherwise starts from background threads.
 */
@ExperimentalCoroutinesApi
public fun initMainThread() {
    getOrCreateMainThread()
}

internal actual fun initCurrentThread(): Thread =
    if (isMainThread()) mainThread else WorkerThread()

@SharedImmutable
private val _mainThread = AtomicReference<Thread?>(null)

internal val mainThread: Thread get() = _mainThread.value ?: getOrCreateMainThread()

private fun getOrCreateMainThread(): Thread {
    require(isMainThread()) {
        "Coroutines must be initialized from the main thread: call 'initMainThread' from the main thread first"
    }
    _mainThread.value?.let { return it }
    return MainThread().also { _mainThread.value = it }
}

private class MainThread : WorkerThread() {
    private val posted = atomic(false)

    private val processQueueBlock: dispatch_block_t =  {
        posted.value = false // next execute will post a fresh task
        while (worker.processQueue()) { /* process all */ }
    }

    init { freeze() }

    override fun execute(block: Runnable) {
        super.execute(block)
        // post to main queue if needed
        if (posted.compareAndSet(false, true)) {
            dispatch_async(dispatch_get_main_queue(), processQueueBlock)
        }
    }

    override fun toString(): String = "MainThread"
}
