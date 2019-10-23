/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import kotlin.native.concurrent.*
import kotlin.test.*

class WorkerCoroutineDispatcherTest : TestBase() {
    private val dispatcher = newSingleThreadContext("WorkerCoroutineDispatcherTest")
    private val mainWorker = Worker.current

    @AfterTest
    fun tearDown() {
        dispatcher.close()
    }

    @Test
    fun testWithContext() = runTest {
        val atomic = AtomicInt(0) // can be captured & shared
        expect(1)
        val result = withContext(dispatcher) {
            expect(2)
            assertEquals(dispatcher.worker, Worker.current)
            atomic.value = 42
            "OK"
        }
        assertEquals(mainWorker, Worker.current)
        assertEquals("OK", result)
        assertEquals(42, atomic.value)
        finish(3)
    }
}