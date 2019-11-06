/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.internal

import kotlin.native.ThreadLocal
import kotlin.native.concurrent.*

internal abstract class Thread {
    abstract fun execute(block: () -> Unit)
}

internal fun currentThread(): Thread = WorkerThread.currentThread

internal data class WorkerThread(val worker: Worker) : Thread() {
    override fun execute(block: () -> Unit) {
        block.freeze()
        worker.execute(TransferMode.SAFE, { block }) {
            it()
        }
    }

    @ThreadLocal
    companion object {
        val currentThread = WorkerThread(Worker.current)
    }

    override fun toString(): String = worker.toString()
}
