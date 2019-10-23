/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import kotlinx.coroutines.intrinsics.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

internal actual fun <T> startDispatchedCoroutine(
    newInterceptor: ContinuationInterceptor?,
    newContext: CoroutineContext,
    block: suspend CoroutineScope.() -> T,
    uCont: Continuation<T>
): DispatchedCoroutine<T> {
    val coroutine = DispatchedCoroutine(newContext, uCont)
    coroutine.initParentJob()
    block.startCoroutineCancellable(coroutine, coroutine)
    return coroutine
}

@Suppress("NOTHING_TO_INLINE") // Save an entry on call stack
internal actual inline fun <T> Continuation<T>.resumeInterceptedCancellableWith(result: Result<T>) {
    intercepted().resumeCancellableWith(result)
}