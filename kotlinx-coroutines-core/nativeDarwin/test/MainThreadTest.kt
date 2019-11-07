/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import kotlin.test.*

class MainThreadTest {
    @Test
    fun testMainThread() {
        println("Current thread: ${currentThread()}")
//        val worker = newSingleThreadContext("Worker")
//        withContext(worker) {
//            println("Current thread: ${currentThread()}")
//        }
//        worker.
    }
}
