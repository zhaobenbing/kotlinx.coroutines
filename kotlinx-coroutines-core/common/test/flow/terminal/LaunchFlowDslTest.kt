/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.flow

import kotlinx.coroutines.*
import kotlin.reflect.*
import kotlin.test.*

class LaunchFlowDslTest : TestBase() {

    @Test
    fun testValidBuilder() = testDsl {
        onEach {}
        catch<TestException> {}
        catch<Error> {}
        finally {}
    }

    @Test
    fun testNoCatch() = testDsl {
        onEach {}
        finally {}
    }

    @Test
    fun testNoFinally() = testDsl {
        onEach {}
        finally {}
    }

    @Test
    fun testNoOnEach() = testDsl(IllegalStateException::class) {
        catch<TestException> {}
        finally {}
    }

    @Test
    @Ignore
    fun testInvalidCatchOrder() = testDsl(IllegalStateException::class) {
        onEach {}
        catch<Throwable> {}
        catch<TestException> {}
        finally {}
    }

    @Test
    fun testOnEachLast() = testDsl(IllegalStateException::class) {
        catch<TestException> {}
        finally {}
        onEach {}
    }

    @Test
    fun testFinallyBeforeCatch() = testDsl(IllegalStateException::class) {
        onEach {}
        finally {}
        catch<TestException> {}
    }

    @Test
    fun testOnEachInTheMiddle() = testDsl(IllegalStateException::class) {
        catch<TestException> {}
        onEach {}
        finally {}
    }

    @Test
    fun testEmpty() = testDsl(IllegalStateException::class) {
    }

    private fun testDsl(expected: KClass<out Throwable>? = null, block: LaunchFlowBuilder<Unit>.() -> Unit) {
        try {
            val builder = LaunchFlowBuilder<Unit>()
            builder.block()
            builder.build()
        } catch (e: Throwable) {
            val clazz = expected ?: error("Unexpected exception: $e")
            assertTrue(clazz.isInstance(e))
        }
    }
}