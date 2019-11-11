/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package kotlinx.coroutines.internal

import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlin.internal.InlineOnly

@InlineOnly
@Suppress("NOTHING_TO_INLINE") // Should be NOP
internal actual inline fun DisposableHandle.asShareable(): DisposableHandle = this

@InlineOnly
@Suppress("NOTHING_TO_INLINE") // Should be NOP
internal actual inline fun CoroutineDispatcher.asShareable(): CoroutineDispatcher = this

@InlineOnly
@Suppress("NOTHING_TO_INLINE") // Should be NOP
internal actual inline fun <T> Continuation<T>.asShareable() : Continuation<T> = this

@InlineOnly
@Suppress("NOTHING_TO_INLINE") // Should be NOP
internal actual inline fun <T> Continuation<T>.asLocal() : Continuation<T> = this

@InlineOnly
@Suppress("NOTHING_TO_INLINE") // Should be NOP
internal actual inline fun <T> Continuation<T>.asLocalOrNull() : Continuation<T>? = this

@InlineOnly
@Suppress("NOTHING_TO_INLINE") // Should be NOP
internal actual inline fun <T> Continuation<T>.useLocal() : Continuation<T> = this

@InlineOnly
@Suppress("NOTHING_TO_INLINE") // Should be NOP
internal actual inline fun <T> Continuation<T>.shareableInterceptedResumeCancellableWith(result: Result<T>) {
    intercepted().resumeCancellableWith(result)
}

@InlineOnly
@Suppress("NOTHING_TO_INLINE") // Save an entry on call stack
internal actual inline fun <T> CancellableContinuationImpl<T>.shareableResume(delegate: Continuation<T>, useMode: Int) =
    resumeImpl(delegate, useMode)

@InlineOnly
@Suppress("NOTHING_TO_INLINE") // Save an entry on call stack
internal actual inline fun isReuseSupportedInPlatform() = true

@InlineOnly
internal actual inline fun <T> ArrayList<T>.addOrUpdate(element: T, update: (ArrayList<T>) -> Unit) {
    add(element)
}

@InlineOnly
internal actual inline fun <T> ArrayList<T>.addOrUpdate(index: Int, element: T, update: (ArrayList<T>) -> Unit) {
    add(index, element)
}

