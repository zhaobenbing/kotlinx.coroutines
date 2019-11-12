/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

@ExperimentalCoroutinesApi
public expect fun newSingleThreadContext(name: String): SingleThreadDispatcher

@ExperimentalCoroutinesApi
public expect abstract class SingleThreadDispatcher : CoroutineDispatcher {
    public abstract fun close()
}