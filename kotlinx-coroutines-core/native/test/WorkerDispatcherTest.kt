/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import kotlinx.coroutines.channels.*
import kotlin.native.concurrent.*
import kotlin.test.*

class WorkerDispatcherTest : TestBase() {
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

    @Test
    fun testLaunchJoin() = runTest {
        val atomic = AtomicInt(0) // can be captured & shared
        expect(1)
        val job = launch(dispatcher) {
            assertEquals(dispatcher.worker, Worker.current)
            atomic.value = 42
        }
        job.join()
        assertEquals(mainWorker, Worker.current)
        assertEquals(42, atomic.value)
        finish(2)
    }

    @Test
    fun testLaunchLazyJoin() = runTest {
        expect(1)
        val job = launch(dispatcher, start = CoroutineStart.LAZY) {
            expect(3)
            assertEquals(dispatcher.worker, Worker.current)
        }
        expect(2)
        job.join() // lazy start here
        finish(4)
    }

    @Test
    fun testAsyncAwait() = runTest {
        val atomic = AtomicInt(0) // can be captured & shared
        expect(1)
        val deferred = async(dispatcher) {
            assertEquals(dispatcher.worker, Worker.current)
            atomic.value = 42
            "OK"
        }
        val result = deferred.await()
        assertEquals(mainWorker, Worker.current)
        assertEquals("OK", result)
        assertEquals(42, atomic.value)
        finish(2)
    }

    @Test
    fun testAsyncLazyAwait() = runTest {
        expect(1)
        val deferred = async(dispatcher, start = CoroutineStart.LAZY) {
            expect(3)
            assertEquals(dispatcher.worker, Worker.current)
            "OK"
        }
        expect(2)
        val result = deferred.await() // lazy start here
        assertEquals("OK", result)
        finish(4)
    }

    @Test
    fun testProduceConsume() = runTest {
        val atomic = AtomicInt(0) // can be captured & shared
        expect(1)
        val channel = produce(dispatcher) {
            assertEquals(dispatcher.worker, Worker.current)
            atomic.value = 42
            expect(2)
            send(Data("A"))
            send(Data("B"))
        }
        val result1 = channel.receive()
        expect(3)
        assertEquals(mainWorker, Worker.current)
        assertEquals("A", result1.s)
        assertTrue(result1.isFrozen)
        assertEquals(42, atomic.value)
        val result2 = channel.receive()
        assertEquals("B", result2.s)
        finish(4)
    }

    @Test
    fun testWithContextDelay() = runTest {
        expect(1)
        withContext(dispatcher) {
            expect(2)
            delay(10)
            assertEquals(dispatcher.worker, Worker.current)
            expect(3)
        }
        finish(4)
    }

    @Test
    fun testWithTimeoutAroundWithContextNoTimeout() = runTest {
        expect(1)
        withTimeout(1000) {
            withContext(dispatcher) {
                expect(2)
            }
        }
        finish(3)
    }

    @Test
    fun testWithTimeoutAroundWithContextTimedOut() = runTest {
        expect(1)
        assertFailsWith<TimeoutCancellationException> {
            withTimeout(100) {
                withContext(dispatcher) {
                    expect(2)
                    delay(1000)
                }
            }
        }
        finish(3)
    }

    private class Data(val s: String)
}