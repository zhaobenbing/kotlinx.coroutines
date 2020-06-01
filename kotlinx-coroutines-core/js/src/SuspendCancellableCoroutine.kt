/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

/**
 * Suspends the coroutine like [suspendCoroutine], but providing a [CancellableContinuation] to
 * the [block]. This function throws a [CancellationException] if the coroutine is cancelled or completed while suspended.
 */
public actual suspend inline fun <T> suspendCancellableCoroutine(
    crossinline block: (CancellableContinuation<T>) -> Unit
): T =
    suspendCoroutineUninterceptedOrReturn { uCont ->
        val cancellable = CancellableContinuationImpl(uCont.intercepted(), resumeMode = MODE_CANCELLABLE)
        /*
         * For non-atomic cancellation we setup parent-child relationship immediately
         * in case when `block` blocks the current thread (e.g. Rx2 with trampoline scheduler), but
         * properly supports cancellation.
         */
        cancellable.initCancellability()
        block(cancellable)
        cancellable.getResult()
    }

/**
 * Suspends the coroutine like [suspendCancellableCoroutine], but with *atomic cancellation*.
 *
 * When the suspended function throws a [CancellationException], it means that the continuation was not resumed.
 * As a side-effect of atomic cancellation, a thread-bound coroutine (to some UI thread, for example) may
 * continue to execute even after it was cancelled from the same thread in the case when the continuation
 * was already resumed and was posted for execution to the thread's queue.
 *
 * @suppress **This an internal API and should not be used from general code.**
 */
@InternalCoroutinesApi
public actual suspend inline fun <T> suspendAtomicCancellableCoroutine(
    crossinline block: (CancellableContinuation<T>) -> Unit
): T =
    suspendCoroutineUninterceptedOrReturn { uCont ->
        val cancellable = CancellableContinuationImpl(uCont.intercepted(), resumeMode = MODE_ATOMIC_DEFAULT)
        block(cancellable)
        cancellable.getResult()
    }

/**
 *  Suspends coroutine similar to [suspendAtomicCancellableCoroutine], but an instance of [CancellableContinuationImpl] is reused if possible.
 */
internal actual suspend inline fun <T> suspendAtomicCancellableCoroutineReusable(
    crossinline block: (CancellableContinuation<T>) -> Unit
): T = suspendCoroutineUninterceptedOrReturn { uCont ->
    val cancellable = getOrCreateCancellableContinuation(uCont.intercepted())
    block(cancellable)
    cancellable.getResult()
}

