/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.intrinsics

import kotlinx.coroutines.*
import kotlinx.coroutines.internal.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

/**
 * Use this function to start coroutine in an "atomic" way, so that it cannot be cancelled
 * while waiting to be dispatched.
 */
internal fun <R, T> (suspend (R) -> T).startCoroutineAtomic(receiver: R, completion: Continuation<T>) =
    runSafely(completion) {
        createCoroutineUnintercepted(receiver, completion).also { it.ensureNeverFrozen() }
            .intercepted().resume(Unit)
    }


/**
 * Use this function to start coroutine in a cancellable way, so that it can be cancelled
 * while waiting to be dispatched.
 */
@InternalCoroutinesApi
public fun <T> (suspend () -> T).startCoroutineCancellable(completion: Continuation<T>) = runSafely(completion) {
    runSafely(completion) {
        createCoroutineUnintercepted(completion).also { it.ensureNeverFrozen() }
            .intercepted().resumeCancellableWith(Result.success(Unit))
    }
}

/**
 * Use this function to start coroutine in a cancellable way, so that it can be cancelled
 * while waiting to be dispatched.
 */
internal fun <R, T> (suspend (R) -> T).startCoroutineCancellable(receiver: R, completion: Continuation<T>) =
    runSafely(completion) {
        createCoroutineUnintercepted(receiver, completion).also { it.ensureNeverFrozen() }
            .intercepted().resumeCancellableWith(Result.success(Unit))
    }

/**
 * Similar to [startCoroutineCancellable], but for already created coroutine.
 * [fatalCompletion] is used only when interception machinery throws an exception
 */
internal fun Continuation<Unit>.startCoroutineCancellable(fatalCompletion: Continuation<*>) =
    runSafely(fatalCompletion) {
        ensureNeverFrozen()
        intercepted().resumeCancellableWith(Result.success(Unit))
    }

/**
 * Runs given block and completes completion with its exception if it occurs.
 * Rationale: [startCoroutineCancellable] is invoked when we are about to run coroutine asynchronously in its own dispatcher.
 * Thus if dispatcher throws an exception during coroutine start, coroutine never completes, so we should treat dispatcher exception
 * as its cause and resume completion.
 */
private inline fun runSafely(completion: Continuation<*>, block: () -> Unit) {
    try {
        block()
    } catch (e: Throwable) {
        completion.resumeWith(Result.failure(e))
    }
}
