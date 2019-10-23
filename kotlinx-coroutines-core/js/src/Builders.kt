/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import kotlinx.coroutines.intrinsics.*
import kotlin.coroutines.*

@Suppress("NOTHING_TO_INLINE") // Save an entry on call stack
internal actual inline fun <T, R> startCoroutine(
    start: CoroutineStart,
    coroutine: AbstractCoroutine<T>,
    receiver: R,
    noinline block: suspend R.() -> T
) =
    startCoroutineImpl(start, coroutine, receiver, block)
