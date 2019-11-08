/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import platform.darwin.*
import kotlin.coroutines.*
import kotlin.native.concurrent.*

internal actual fun createMainDispatcher(default: CoroutineDispatcher): MainCoroutineDispatcher =
    DarwinMainDispatcher(false)

private class DarwinMainDispatcher(
    private val invokeImmediately: Boolean
) : MainCoroutineDispatcher(), ThreadBoundInterceptor, Delay {
    override val thread = mainThread
    override val immediate: MainCoroutineDispatcher =
        if (invokeImmediately) this else DarwinMainDispatcher(true)

    init { freeze() }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
        !invokeImmediately || currentThread() !== mainThread

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatch_async(dispatch_get_main_queue()) {
            block.run()
        }
    }
    
    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, timeMillis), dispatch_get_main_queue()) {
            continuation.resume(Unit)
            // todo: dispose
        }
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable): DisposableHandle {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, timeMillis), dispatch_get_main_queue()) {
            block.run()
        }
        // todo: dispose
        return NonDisposableHandle
    }

    override fun toString(): String =
        "MainDispatcher${ if(invokeImmediately) "[immediate]" else "" }"
}
