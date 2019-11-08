/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import kotlin.test.*

class MainDispatcherTest : TestBase() {
    @Test
    fun testWithContext() {
        if (mainThread == currentThread()) return // skip if already on the main thread
        runTest {
            expect(1)
            withContext(Dispatchers.Main) {
                expect(2)
                assertEquals(mainThread, currentThread())
            }
            finish(3)
        }
    }
}
