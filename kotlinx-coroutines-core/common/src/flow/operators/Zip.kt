/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmMultifileClass
@file:JvmName("FlowKt")
@file:Suppress("UNCHECKED_CAST")

package kotlinx.coroutines.flow

import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.internal.*
import kotlinx.coroutines.selects.*
import kotlin.coroutines.*
import kotlin.jvm.*
import kotlinx.coroutines.flow.unsafeFlow as flow

/**
 * Returns a [Flow] whose values are generated with [transform] function by combining
 * the most recently emitted values by each flow.
 *
 * It can be demonstrated with the following example:
 * ```
 * val flow = flowOf(1, 2).delayEach(10)
 * val flow2 = flowOf("a", "b", "c").delayEach(15)
 * flow.combineLatest(flow2) { i, s -> i.toString() + s }.collect {
 *     println(it) // Will print "1a 2a 2b 2c"
 * }
 * ```
 */
@ExperimentalCoroutinesApi
public fun <T1, T2, R> Flow<T1>.combineLatest(other: Flow<T2>, transform: suspend (T1, T2) -> R): Flow<R> = flow {
    coroutineScope {
        val firstChannel = asFairChannel(this@combineLatest)
        val secondChannel = asFairChannel(other)
        var firstValue: Any? = null
        var secondValue: Any? = null
        var firstIsClosed = false
        var secondIsClosed = false

        /*
         * Fun fact, this select **semantically** equivalent of the following:
         * ```
         * selectWhile<Unit> {
         *     channel.onReceive {
         *         emitCombined(...)
         *     }
         *     channel2.onReceive {
         *         emitCombined(...)
         *     }
         * }
         * ```
         * but we are waiting for `channels` branch to get merged where we will change semantics of the select
         * to ignore finished clauses.
         *
         * Instead (especially in the face of non-fair channels) we are using our own hand-rolled select emulation
         * on top of previous select.
         */
        while (!firstIsClosed || !secondIsClosed) {
            select<Unit> {
                onReceive(firstIsClosed, firstChannel, { firstIsClosed = true }) { value ->
                    firstValue = value
                    if (secondValue !== null) {
                        emit(transform(NULL.unbox(firstValue), NULL.unbox(secondValue)))
                    }
                }

                onReceive(secondIsClosed, secondChannel, { secondIsClosed = true }) { value ->
                    secondValue = value
                    if (firstValue !== null) {
                        emit(transform(NULL.unbox(firstValue), NULL.unbox(secondValue)))
                    }
                }
            }
        }
    }
}

/**
 * Returns a [Flow] whose values are generated with [transform] function by combining
 * the most recently emitted values by each flow.
 */
@ExperimentalCoroutinesApi
public inline fun <T1, T2, T3, R> Flow<T1>.combineLatest(
    other: Flow<T2>,
    other2: Flow<T3>,
    crossinline transform: suspend (T1, T2, T3) -> R
): Flow<R> = (this as Flow<*>).combineLatest(other, other2) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3
    )
}

/**
 * Returns a [Flow] whose values are generated with [transform] function by combining
 * the most recently emitted values by each flow.
 */
@ExperimentalCoroutinesApi
public inline fun <T1, T2, T3, T4, R> Flow<T1>.combineLatest(
    other: Flow<T2>,
    other2: Flow<T3>,
    other3: Flow<T4>,
    crossinline transform: suspend (T1, T2, T3, T4) -> R
): Flow<R> = (this as Flow<*>).combineLatest(other, other2, other3) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4
    )
}

/**
 * Returns a [Flow] whose values are generated with [transform] function by combining
 * the most recently emitted values by each flow.
 */
@ExperimentalCoroutinesApi
public inline fun <T1, T2, T3, T4, T5, R> Flow<T1>.combineLatest(
    other: Flow<T2>,
    other2: Flow<T3>,
    other3: Flow<T4>,
    other4: Flow<T5>,
    crossinline transform: suspend (T1, T2, T3, T4, T5) -> R
): Flow<R> = (this as Flow<*>).combineLatest(other, other2, other3, other4) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5
    )
}

/**
 * Returns a [Flow] whose values are generated with [transform] function by combining
 * the most recently emitted values by each flow.
 */
@ExperimentalCoroutinesApi
public inline fun <reified T, R> Flow<T>.combineLatest(vararg others: Flow<T>, crossinline transform: suspend (Array<T>) -> R): Flow<R> =
    combineLatest(*others, arrayFactory = { arrayOfNulls(others.size + 1) }, transform = { transform(it) })

/**
 * Returns a [Flow] whose values are generated with [transform] function by combining
 * the most recently emitted values by each flow.
 */
@PublishedApi
internal fun <T, R> Flow<T>.combineLatest(vararg others: Flow<T>, arrayFactory: () -> Array<T?>, transform: suspend (Array<T>) -> R): Flow<R> = flow {
    coroutineScope {
        val size = others.size + 1
        val channels =
            Array(size) { if (it == 0) asFairChannel(this@combineLatest) else asFairChannel(others[it - 1]) }
        val latestValues = arrayOfNulls<Any?>(size)
        val isClosed = Array(size) { false }

        // See flow.combineLatest(other) for explanation.
        while (!isClosed.all { it }) {
            select<Unit> {
                for (i in 0 until size) {
                    onReceive(isClosed[i], channels[i], { isClosed[i] = true }) { value ->
                        latestValues[i] = value
                        if (latestValues.all { it !== null }) {
                            val arguments = arrayFactory()
                            for (index in 0 until size) {
                                arguments[index] = NULL.unbox(latestValues[index])
                            }
                            emit(transform(arguments as Array<T>))
                        }
                    }
                }
            }
        }
    }
}

private inline fun SelectBuilder<Unit>.onReceive(
    isClosed: Boolean,
    channel: ReceiveChannel<Any>,
    crossinline onClosed: () -> Unit,
    noinline onReceive: suspend (value: Any) -> Unit
) {
    if (isClosed) return
    channel.onReceiveOrNull {
        if (it === null) onClosed()
        else onReceive(it)
    }
}

// Channel has any type due to onReceiveOrNull. This will be fixed after receiveOrClosed
private fun CoroutineScope.asFairChannel(flow: Flow<*>): ReceiveChannel<Any> = produce {
    val channel = channel as ChannelCoroutine<Any>
    flow.collect { value ->
        channel.sendFair(value ?: NULL)
    }
}


/**
 * Zips values from the current flow (`this`) with [other] flow using provided [transform] function applied to each pair of values.
 * The resulting flow completes as soon as one of the flows completes and cancel is called on the remaining flow.
 *
 * It can be demonstrated with the following example:
 * ```
 * val flow = flowOf(1, 2, 3).delayEach(10)
 * val flow2 = flowOf("a", "b", "c", "d").delayEach(15)
 * flow.zip(flow2) { i, s -> i.toString() + s }.collect {
 *     println(it) // Will print "1a 2b 3c"
 * }
 * ```
 */
@ExperimentalCoroutinesApi
public fun <T1, T2, R> Flow<T1>.zip2(other: Flow<T2>, transform: suspend (T1, T2) -> R): Flow<R> = flow {
    coroutineScope {
        val first = asChannel(this@zip2)
        val second = asChannel(other)
        /*
         * This approach only works with rendezvous channel and is required to enforce correctness
         * in the following scenario:
         * ```
         * val f1 = flow { emit(1); delay(Long.MAX_VALUE) }
         * val f2 = flowOf(1)
         * f1.zip(f2) { ... }
         * ```
         *
         * Invariant: this clause is invoked only when all elements from the channel were processed (=> rendezvous restriction).
         */
        (second as SendChannel<*>).invokeOnClose {
            if (!first.isClosedForReceive) first.cancel(AbortFlowException())
        }

        val otherIterator = second.iterator()
        try {
            first.consumeEach { value ->
                if (!otherIterator.hasNext()) {
                    return@consumeEach
                }
                val secondValue = NULL.unbox<T2>(otherIterator.next())
                emit(transform(NULL.unbox(value), NULL.unbox(secondValue)))
            }
        } catch (e: AbortFlowException) {
            // complete
        } finally {
            if (!second.isClosedForReceive) second.cancel(AbortFlowException())
        }
    }
}

// Channel has any type due to onReceiveOrNull. This will be fixed after receiveOrClosed
private fun CoroutineScope.asChannel(flow: Flow<*>): ReceiveChannel<Any> = produce {
    flow.collect { value ->
        channel.send(value ?: NULL)
    }
}

@ExperimentalCoroutinesApi
public fun <T1, T2, R> Flow<T1>.zip(other: Flow<T2>, transform: suspend (T1, T2) -> R): Flow<R> = flow {
    coroutineScope {
        val first: ZipChannel = asZipChannel(this@zip)
        val second: ZipChannel = asZipChannel(other)

        try {
            while (true) {
                // End of first flow
                val firstValue = first.receive() ?: return@coroutineScope
                val secondValue = second.receive() ?: return@coroutineScope
                emit(transform(NULL.unbox(firstValue), NULL.unbox(secondValue)))
            }
        } catch (e: AbortFlowException) {
            // complete
        } finally {
            // TODO does not look right
            second.close(AbortFlowException())
            first.close(AbortFlowException())
        }
    }
}

private fun CoroutineScope.asZipChannel(flow: Flow<*>): ZipChannel {
    val channel = ZipChannel()
    launch {
        var throwable: Throwable? = null
        try {
            flow.collect { value ->
                channel.send(value ?: NULL)
            }
        } catch (e: Throwable) {
            throwable = e
            throw e
        } finally {
            channel.close(throwable)
        }
    }
    return channel
}


// TODO cancellation bound to outer job is not implemented
private class ZipChannel() {

    private var enqueuedValue: Any = NULL_VALUE
    private val rendezvous = atomic<Any?>(null)

    // Any or null if closed normally
    suspend fun receive(): Any? {
        val result = poll()
        if (result != null) {
            return result.unwrap()
        }

        return suspendAtomicCancellableCoroutine {
            if (!rendezvous.compareAndSet(null, it)) {
                it.resumeWithResult(poll()!!)
            }
        }
    }

    private fun poll(): Any? {
        val sender = rendezvous.value
        if (sender === null) return null
        if (sender is Closed) return sender

        val result = enqueuedValue
        enqueuedValue = NULL_VALUE
        rendezvous.value = null
        (sender as Continuation<Unit>).resumeWith(Result.success(Unit))
        return result
    }

    private fun Any.unwrap(): Any? {
        return when {
            this !is Closed -> this
            cause === null -> null
            else -> throw cause
        }
    }

    private fun Continuation<Any?>.resumeWithResult(result: Any) {
        when {
            result !is Closed -> resumeWith(Result.success(result))
            result.cause === null -> resumeWith(Result.success(null))
            else -> resumeWith(Result.failure(result.cause))
        }
    }

    suspend fun send(value: Any) {
        assert { value !== NULL_VALUE }
        if (offer(value)) return
        suspendAtomicCancellableCoroutine<Unit?> {
            enqueuedValue = value
            if (!rendezvous.compareAndSet(null, it)) {
                enqueuedValue = NULL_VALUE
                val result = offer(value)
                assert { result }
                it.resume(Unit)
            }
        }
    }

    private fun offer(value: Any): Boolean {
        val receiver = rendezvous.value
        while (true) {
            if (receiver !== null) {
                if (receiver is Closed) {
                    throw receiver.cause ?: AbortFlowException()
                }

                rendezvous.value = null
                (receiver as Continuation<Any>).resumeWith(Result.success(value))
                return true
            }

            return false
        }
    }

    fun close(cause: Throwable? = null) {
        val closed = Closed(cause)
        while (true) {
            val previous = rendezvous.value
            if (previous is Closed) return
            if (rendezvous.compareAndSet(previous, closed)) {
                if (previous is Continuation<*>) {
                    previous as Continuation<Any?>
                    if (cause === null) previous.resumeWith(Result.success(null))
                    else previous.resumeWith(Result.failure(cause))
                }
                return
            }
        }
    }

    private class Closed(@JvmField val cause: Throwable? = null)
}