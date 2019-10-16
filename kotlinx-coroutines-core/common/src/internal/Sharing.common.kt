/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.internal

import kotlinx.coroutines.*
import kotlin.coroutines.*

internal expect fun DisposableHandle.asShareable(): DisposableHandle
internal expect fun CoroutineDispatcher.asShareable(): CoroutineDispatcher
internal expect fun <T> Continuation<T>.asShareable() : Continuation<T>
internal expect fun <T> Continuation<T>.asLocal() : Continuation<T>
internal expect fun <T> Continuation<T>.asLocalOrNull() : Continuation<T>?
internal expect fun <T> Continuation<T>.useLocal() : Continuation<T>
internal expect fun <T> Continuation<T>.shareableInterceptedResumeCancellableWith(result: Result<T>)
internal expect fun <T> CancellableContinuationImpl<T>.shareableResume(delegate: Continuation<T>, useMode: Int)
internal expect fun isReuseSupportedInPlatform(): Boolean
