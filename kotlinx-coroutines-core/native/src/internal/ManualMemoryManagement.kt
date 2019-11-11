/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.internal

import kotlin.native.concurrent.*

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun disposeLockFreeLinkedList(list: () -> LockFreeLinkedListNode?) {
    // only needed on Kotlin/Native
    val head = list() ?: return
    var cur = head
    do {
        val next = cur.nextNode
        cur.unlinkRefs(NullNodeRef)
        cur = next
    } while (cur !== head)
}

private object NullNodeRef : LockFreeLinkedListNode() {
    init {
        initRemoved()
        freeze()
    }
}