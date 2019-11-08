/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import kotlin.native.ThreadLocal
import kotlin.native.concurrent.*

internal abstract class Thread {
    abstract fun execute(block: Runnable)
    abstract fun parkNanos(timeout: Long)
}

internal inline fun Thread.executeFrozen(crossinline block: () -> Unit) {
    val runnable = Runnable { block() }
    runnable.freeze()
    execute(runnable)
}

@ThreadLocal
private val currentThread: Thread = initCurrentThread()

internal fun currentThread(): Thread = currentThread

internal expect fun initCurrentThread(): Thread

internal open class WorkerThread(val worker: Worker = Worker.current) : Thread() {
    override fun execute(block: Runnable) {
        block.freeze()
        worker.execute(TransferMode.SAFE, { block }) {
            it.run()
        }
    }

    override fun parkNanos(timeout: Long) {
        // Note: worker is parked in microseconds
        worker.park(timeout / 1000L, process = true)
    }

    override fun equals(other: Any?): Boolean = other is WorkerThread && other.worker == worker
    override fun hashCode(): Int = worker.hashCode()
    override fun toString(): String = worker.name
}

internal interface ThreadBoundInterceptor {
    val thread: Thread
}

internal fun ThreadBoundInterceptor.checkCurrentThread() {
    val current = currentThread()
    check(current == thread) { "This dispatcher can be used only from a single thread $thread, but now in $current" }
}