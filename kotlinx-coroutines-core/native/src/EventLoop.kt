/*
 * Copyright 2016-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import kotlinx.cinterop.*
import kotlin.coroutines.*
import kotlin.native.concurrent.*
import kotlin.system.*

internal actual abstract class EventLoopImplPlatform: EventLoop() {
    protected actual fun unpark() {
        /*
         * Does nothing, because we only work with EventLoop in Kotlin/Native from a single thread where
         * it was created. All tasks that come from other threads are passed into the owner thread via
         * Worker.execute and its queueing mechanics.
         */
    }
    
    protected actual fun reschedule(now: Long, delayedTask: EventLoopImplBase.DelayedTask): Unit =
        loopWasShutDown()
}

internal class EventLoopImpl: EventLoopImplBase() {
    init { ensureNeverFrozen() }
    
    val shareable = ShareableEventLoop(StableRef.create(this), Worker.current)

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable): DisposableHandle =
        scheduleInvokeOnTimeout(timeMillis, block)

    override fun shutdown() {
        super.shutdown()
        shareable.ref.dispose()
    }
}

internal class ShareableEventLoop(
    val ref: StableRef<EventLoopImpl>,
    override val worker: Worker
) : WorkerCoroutineDispatcher(), Delay {
    init { freeze() }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        checkCurrentWorker()
        ref.get().scheduleResumeAfterDelay(timeMillis, continuation)
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable): DisposableHandle {
        checkCurrentWorker()
        return ref.get().invokeOnTimeout(timeMillis, block)
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        checkCurrentWorker()
        ref.get().dispatch(context, block)
    }

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        checkCurrentWorker()
        return ref.get().interceptContinuation(continuation)
    }

    @InternalCoroutinesApi
    override fun releaseInterceptedContinuation(continuation: Continuation<*>) {
        checkCurrentWorker()
        ref.get().releaseInterceptedContinuation(continuation)
    }
}

internal actual fun createEventLoop(): EventLoop = EventLoopImpl()

internal actual fun nanoTime(): Long = getTimeNanos()