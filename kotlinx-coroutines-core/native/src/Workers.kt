/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import kotlinx.atomicfu.*
import kotlin.coroutines.*
import kotlin.native.concurrent.*

@ExperimentalCoroutinesApi
public actual fun newSingleThreadContext(name: String): SingleThreadDispatcher =
    WorkerCoroutineDispatcherImpl(name).apply { start() }

@ExperimentalCoroutinesApi
@Suppress("ACTUAL_WITHOUT_EXPECT")
public actual abstract class SingleThreadDispatcher : CoroutineDispatcher() {
    internal abstract val thread: Thread

    public actual abstract fun close()

    // for tests
    internal actual open fun closeAndBlockUntilTermination() { close() }
}

private class WorkerCoroutineDispatcherImpl(name: String) : SingleThreadDispatcher(), ThreadBoundInterceptor, Delay {
    private val worker = Worker.start(name = name)
    override val thread = WorkerThread(worker)
    private val isClosed = atomic(false)
    
    init { freeze() }

    fun start() {
        worker.execute(TransferMode.SAFE, { this }) { it.run() }
    }

    fun run() {
        runEventLoop(ThreadLocalEventLoop.eventLoop) { isClosed.value }
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        checkCurrentThread()
        (ThreadLocalEventLoop.eventLoop as Delay).scheduleResumeAfterDelay(timeMillis, continuation)
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable): DisposableHandle {
        checkCurrentThread()
        return (ThreadLocalEventLoop.eventLoop as Delay).invokeOnTimeout(timeMillis, block)
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        checkCurrentThread()
        ThreadLocalEventLoop.eventLoop.dispatch(context, block)
    }

    override fun close() {
        isClosed.value = true
        worker.requestTermination()
    }

    override fun closeAndBlockUntilTermination() {
        isClosed.value = true
        worker.requestTermination().result // Note: calling "result" blocks
    }
}