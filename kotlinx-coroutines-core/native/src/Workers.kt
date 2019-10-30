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
public abstract class WorkerCoroutineDispatcher : CoroutineDispatcher() {
    public abstract val worker: Worker

    protected fun checkCurrentWorker() {
        val current = Worker.current
        check(current == worker) { "This dispatcher can be used only from a single worker $worker, but now in $current" }
    }
}

@ExperimentalCoroutinesApi
@Suppress("ACTUAL_WITHOUT_EXPECT")
public actual abstract class SingleThreadDispatcher : WorkerCoroutineDispatcher() {
    public actual abstract fun close()

    internal abstract fun closeAndBlockUntilTermination() // for tests
}

private class WorkerCoroutineDispatcherImpl(name: String) : SingleThreadDispatcher(), Delay {
    public override val worker = Worker.start(name = name)
    private val isClosed = atomic(false)
    
    init { freeze() }

    fun start() {
        worker.execute(TransferMode.SAFE, { this }) { it.run() }
    }

    fun run() {
        runEventLoop(ThreadLocalEventLoop.eventLoop) { isClosed.value }
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        checkCurrentWorker()
        (ThreadLocalEventLoop.eventLoop as Delay).scheduleResumeAfterDelay(timeMillis, continuation)
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable): DisposableHandle {
        checkCurrentWorker()
        return (ThreadLocalEventLoop.eventLoop as Delay).invokeOnTimeout(timeMillis, block)
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        checkCurrentWorker()
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