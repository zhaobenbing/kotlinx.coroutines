/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

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

    override fun dispatch(context: CoroutineContext, block: Runnable) =
        mainThread.execute(block)
    
    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        TODO("not implemented")
    }

    override fun toString(): String =
        "MainDispatcher${ if(invokeImmediately) "[immediate]" else "" }"
}
