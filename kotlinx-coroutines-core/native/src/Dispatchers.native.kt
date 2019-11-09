/*
 * Copyright 2016-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import kotlin.coroutines.*

public actual object Dispatchers {
    public actual val Default: CoroutineDispatcher = LazyDefaultDispatcher()
    public actual val Main: MainCoroutineDispatcher = createMainDispatcher(Default)
    public actual val Unconfined: CoroutineDispatcher get() = kotlinx.coroutines.Unconfined // Avoid freezing
}

internal expect fun createMainDispatcher(default: CoroutineDispatcher): MainCoroutineDispatcher

// Create DefaultDispatcher thread only when explicitly requested
private class LazyDefaultDispatcher : CoroutineDispatcher(), Delay, ThreadBoundInterceptor {
    private val delegate by lazy { newSingleThreadContext("DefaultDispatcher") }

    override val thread: Thread
        get() = delegate.thread
    override fun dispatch(context: CoroutineContext, block: Runnable) =
        delegate.dispatch(context, block)
    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) =
        (delegate as Delay).scheduleResumeAfterDelay(timeMillis, continuation)
    override fun invokeOnTimeout(timeMillis: Long, block: Runnable): DisposableHandle =
        (delegate as Delay).invokeOnTimeout(timeMillis, block)
    override fun toString(): String =
        delegate.toString()
}