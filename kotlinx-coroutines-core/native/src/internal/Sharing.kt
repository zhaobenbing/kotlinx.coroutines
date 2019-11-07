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
    else -> ShareableContinuation(this)
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
    if (currentThread() == thread) {
        useRef().intercepted().resumeCancellableWith(result)
    } else {
        thread.executeFrozen {
            useRef().intercepted().resumeCancellableWith(result)
        }
    }
}

internal actual fun <T> CancellableContinuationImpl<T>.shareableResume(delegate: Continuation<T>, useMode: Int) {
    if (delegate is ShareableContinuation) {
        if (currentThread() == delegate.thread) {
            resumeImpl(delegate.useRef(), useMode)
        } else {
            delegate.thread.executeFrozen {
                resumeImpl(delegate.useRef(), useMode)
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

private open class ShareableObject<T : Any>(obj: T) {
    val thread: Thread = currentThread()

    // todo: this is best effort (fail-fast) double-dispose protection, does not provide memory safety guarantee
    private val _ref = atomic<StableRef<T>?>(StableRef.create(obj))

    fun localRef(): T {
        val current = currentThread()
        if (current != thread) error("Can be used only from thread $thread but now in $current")
        val ref = _ref.value ?: error("Ref was already used")
        return ref.get()
    }

    fun localRefOrNull(): T? {
        val current = currentThread()
        if (current != thread) return null
        val ref = _ref.value ?: error("Ref was already used")
        return ref.get()
    }

    fun useRef(): T {
        val current = currentThread()
        if (current != thread) error("Can be used only from $thread but now in $current")
        val ref = _ref.getAndSet(null) ?: error("Ref was already used")
        return ref.get().also { ref.dispose() }
    }

    override fun toString(): String =
        "Shareable[${if (currentThread() == thread) _ref.value?.get()?.toString() ?: "used" else "thread!=$thread"}]"
}

private class ShareableContinuation<T>(
    cont: Continuation<T>
) : ShareableObject<Continuation<T>>(cont), Continuation<T> {
    override val context: CoroutineContext = cont.context

    override fun resumeWith(result: Result<T>) {
        if (currentThread() == thread) {
            useRef().resumeWith(result)
        } else {
            thread.executeFrozen {
                useRef().resumeWith(result)
            }
        }
    }
}

private class ShareableDisposableHandle(
    handle: DisposableHandle
) : ShareableObject<DisposableHandle>(handle), DisposableHandle {
    override fun dispose() {
        if (currentThread() == thread) {
            useRef().dispose()
        } else {
            thread.executeFrozen {
                useRef().dispose()
            }
        }
    }
}