/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.internal

import kotlinx.atomicfu.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlin.native.concurrent.*

internal actual fun DisposableHandle.asShareable(): DisposableHandle = when (this) {
    is ShareableDisposableHandle -> this
    else -> ShareableDisposableHandle(this)
}

internal actual fun CoroutineDispatcher.asShareable(): CoroutineDispatcher = when (this) {
    is EventLoopImpl -> shareable
    else -> this
}

internal actual fun <T> Continuation<T>.asShareable() : Continuation<T> = when (this) {
    is ShareableContinuation<T> -> this
    else -> ShareableContinuation(this, Worker.current)
}

internal actual fun <T> Continuation<T>.asLocal() : Continuation<T> = when (this) {
    is ShareableContinuation -> localRef()
    else -> this
}

internal actual fun <T> Continuation<T>.asLocalOrNull() : Continuation<T>? = when (this) {
    is ShareableContinuation -> localRefOrNull()
    else -> this
}

internal actual fun <T> Continuation<T>.useLocal() : Continuation<T> = when (this) {
    is ShareableContinuation -> useRef()
    else -> this
}

internal actual fun <T> Continuation<T>.shareableInterceptedResumeCancellableWith(result: Result<T>) {
    this as ShareableContinuation<T> // must have been shared
    if (Worker.current == worker) {
        useRef().intercepted().resumeCancellableWith(result)
    } else {
        result.freeze() // transferring result back -- it must be frozen
        worker.execute(TransferMode.SAFE, { SharedResult(this, result) }) {
            it.so.useRef().intercepted().resumeCancellableWith(it.result)
        }
    }
}

internal actual fun <T> CancellableContinuationImpl<T>.shareableResume(delegate: Continuation<T>, useMode: Int) {
    if (delegate is ShareableContinuation) {
        if (Worker.current == delegate.worker) {
            resumeImpl(delegate.useRef(), useMode)
        } else {
            delegate.worker.execute(TransferMode.SAFE, { TaskMode(this, useMode) }) {
                it.task.resumeImpl((it.task.delegate as ShareableContinuation).useRef(), it.useMode)
            }
        }
        return
    }
    resumeImpl(delegate, useMode)
}

internal actual fun isReuseSupportedInPlatform() = false

internal actual inline fun <T> ArrayList<T>.addOrUpdate(element: T, update: (ArrayList<T>) -> Unit) {
    if (isFrozen) {
        val list = ArrayList<T>(size + 1)
        list.addAll(this)
        list.add(element)
        update(list)
    } else {
        add(element)
    }
}

internal actual inline fun <T> ArrayList<T>.addOrUpdate(index: Int, element: T, update: (ArrayList<T>) -> Unit) {
    if (isFrozen) {
        val list = ArrayList<T>(size + 1)
        list.addAll(this)
        list.add(index, element)
        update(list)
    } else {
        add(index, element)
    }
}

private class SharedResult<T : Any, R>(val so: ShareableObject<T>, val result: R)
private class TaskMode<T>(val task: CancellableContinuationImpl<T>, val useMode: Int)

private open class ShareableObject<T : Any>(obj: T, val worker: Worker) {
    // todo: this is best effort (fail-fast) double-dispose protection, does not provide memory safety guarantee
    private val _ref = atomic<StableRef<T>?>(StableRef.create(obj))

    fun localRef(): T {
        val current = Worker.current
        if (current != worker) error("Can be used only from $worker but now in $current")
        val ref = _ref.value ?: error("Ref was already used")
        return ref.get()
    }

    fun localRefOrNull(): T? {
        val current = Worker.current
        if (current != worker) return null
        val ref = _ref.value ?: error("Ref was already used")
        return ref.get()
    }

    fun useRef(): T {
        val current = Worker.current
        if (current != worker) error("Can be used only from $worker but now in $current")
        val ref = _ref.getAndSet(null) ?: error("Ref was already used")
        return ref.get().also { ref.dispose() }
    }


    override fun toString(): String =
        "Shareable[${if (Worker.current == worker) _ref.value?.get()?.toString() ?: "used" else "worker!=$worker"}]"
}

private class ShareableContinuation<T>(
    cont: Continuation<T>, worker: Worker
) : ShareableObject<Continuation<T>>(cont, worker), Continuation<T> {
    override val context: CoroutineContext = cont.context

    override fun resumeWith(result: Result<T>) {
        if (Worker.current == worker) {
            useRef().resumeWith(result)
        } else {
            result.freeze()
            worker.execute(TransferMode.SAFE, { SharedResult(this, result) }) {
                it.so.useRef().resumeWith(it.result)
            }
        }
    }
}

private class ShareableDisposableHandle(
    handle: DisposableHandle
) : ShareableObject<DisposableHandle>(handle, Worker.current), DisposableHandle {
    override fun dispose() {
        if (Worker.current == worker) {
            useRef().dispose()
        } else {
            worker.execute(TransferMode.SAFE, { this }) {
                it.useRef().dispose()
            }
        }
    }
}