/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.internal

import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

@Suppress("NOTHING_TO_INLINE") // Should be NOP
internal actual inline fun DisposableHandle.asShareable(): DisposableHandle = this

@Suppress("NOTHING_TO_INLINE") // Should be NOP
internal actual inline fun CoroutineDispatcher.asShareable(): CoroutineDispatcher = this

@Suppress("NOTHING_TO_INLINE") // Should be NOP
internal actual inline fun <T> Continuation<T>.asShareable() : Continuation<T> = this

@Suppress("NOTHING_TO_INLINE") // Should be NOP
internal actual inline fun <T> Continuation<T>.asLocal() : Continuation<T> = this

@Suppress("NOTHING_TO_INLINE") // Should be NOP
internal actual inline fun <T> Continuation<T>.asLocalOrNull() : Continuation<T>? = this

@Suppress("NOTHING_TO_INLINE") // Should be NOP
internal actual inline fun <T> Continuation<T>.useLocal() : Continuation<T> = this

@Suppress("NOTHING_TO_INLINE") // Save an entry on call stack
internal actual inline fun <T> Continuation<T>.shareableInterceptedResumeCancellableWith(result: Result<T>) {
    intercepted().resumeCancellableWith(result)
}

@Suppress("NOTHING_TO_INLINE") // Save an entry on call stack
internal actual inline fun <T> CancellableContinuationImpl<T>.shareableResume(delegate: Continuation<T>, useMode: Int) =
    resumeImpl(delegate, useMode)

@Suppress("NOTHING_TO_INLINE") // Save an entry on call stack
internal actual inline fun CancellableContinuationImpl<*>.isReusablePlatform() =
    delegate is DispatchedContinuation<*> && delegate.isReusable

@Suppress("NOTHING_TO_INLINE") // Save an entry on call stack
internal actual inline fun isReuseSupportedPlatform() = true
