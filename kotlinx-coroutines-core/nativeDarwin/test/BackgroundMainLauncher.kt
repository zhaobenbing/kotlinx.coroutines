/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import platform.darwin.*
import kotlin.native.concurrent.*
import kotlin.native.internal.test.*
import kotlin.system.*

// This is a separate entry point for tests
fun mainBackground(args: Array<String>) {
    initMainThread()
    val worker = Worker.start(name = "main-background")
    worker.execute(TransferMode.SAFE, { args.freeze() }) {
        val result = testLauncherEntryPoint(it)
        exitProcess(result)
    }
    dispatch_main()
}