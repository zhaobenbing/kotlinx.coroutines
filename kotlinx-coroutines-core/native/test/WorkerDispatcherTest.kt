/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.native.concurrent.*
import kotlin.test.*

class WorkerDispatcherTest : TestBase() {
    private val dispatcher = newSingleThreadContext("WorkerCoroutineDispatcherTest")
    private val mainThread = currentThread()

    @AfterTest
    fun tearDown() {
        dispatcher.closeAndBlockUntilTermination()
    }

    @Test
    fun testWithContext() = runTest {
        val atomic = AtomicInt(0) // can be captured & shared
        expect(1)
        val result = withContext(dispatcher) {
            expect(2)
            assertEquals(dispatcher.thread, currentThread())
            atomic.value = 42
            "OK"
        }
        assertEquals(mainThread, currentThread())
        assertEquals("OK", result)
        assertEquals(42, atomic.value)
        finish(3)
    }

    @Test
    fun testLaunchJoin() = runTest {
        val atomic = AtomicInt(0) // can be captured & shared
        expect(1)
        val job = launch(dispatcher) {
            assertEquals(dispatcher.thread, currentThread())
            atomic.value = 42
        }
        job.join()
        assertEquals(mainThread, currentThread())
        assertEquals(42, atomic.value)
        finish(2)
    }

    @Test
    fun testLaunchLazyJoin() = runTest {
        expect(1)
        val job = launch(dispatcher, start = CoroutineStart.LAZY) {
            expect(3)
            assertEquals(dispatcher.thread, currentThread())
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
            assertEquals(dispatcher.thread, currentThread())
            atomic.value = 42
            "OK"
        }
        val result = deferred.await()
        assertEquals(mainThread, currentThread())
        assertEquals("OK", result)
        assertEquals(42, atomic.value)
        finish(2)
    }

    @Test
    fun testAsyncLazyAwait() = runTest {
        expect(1)
        val deferred = async(dispatcher, start = CoroutineStart.LAZY) {
            expect(3)
            assertEquals(dispatcher.thread, currentThread())
            "OK"
        }
        expect(2)
        val result = deferred.await() // lazy start here
        assertEquals("OK", result)
        finish(4)
    }

    @Test
    fun testProduceConsumeRendezvous() = checkProduceConsume(Channel.RENDEZVOUS)

    @Test
    fun testProduceConsumeUnlimited() = checkProduceConsume(Channel.UNLIMITED)

    @Test
    fun testProduceConsumeBuffered() = checkProduceConsume(10)

    private fun checkProduceConsume(capacity: Int) {
        runTest {
            val atomic = AtomicInt(0) // can be captured & shared
            expect(1)
            val channel = produce(dispatcher, capacity) {
                assertEquals(dispatcher.thread, currentThread())
                atomic.value = 42
                expect(2)
                send(Data("A"))
                send(Data("B"))
            }
            val result1 = channel.receive()
            expect(3)
            assertEquals(mainThread, currentThread())
            assertEquals("A", result1.s)
            assertTrue(result1.isFrozen)
            assertEquals(42, atomic.value)
            val result2 = channel.receive()
            assertEquals("B", result2.s)
            finish(4)
        }
    }

    @Test
    fun testFlowOn() = runTest {
        expect(1)
        val flow = flow {
            expect(3)
            assertEquals(dispatcher.thread, currentThread())
            emit(Data("A"))
            emit(Data("B"))
        }.flowOn(dispatcher)
        expect(2)
        val result = flow.toList()
        assertEquals(listOf(Data("A"), Data("B")), result)
        assertTrue(result.all { it.isFrozen })
        finish(4)
    }

    @Test
    fun testWithContextDelay() = runTest {
        expect(1)
        withContext(dispatcher) {
            expect(2)
            delay(10)
            assertEquals(dispatcher.thread, currentThread())
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

    private data class Data(val s: String)
}