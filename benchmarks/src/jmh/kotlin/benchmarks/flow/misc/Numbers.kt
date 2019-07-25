/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */


package benchmarks.flow.misc

import benchmarks.flow.scrabble.flow
import io.reactivex.*
import io.reactivex.functions.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.openjdk.jmh.annotations.*
import java.util.concurrent.*

/*
 * Note: 128 is default Rx buffer size (SpscArrayQueue)
 *
 * Benchmark            Mode  Cnt    Score   Error  Units
 * Numbers.zip          avgt    7  396.547 ± 3.354  us/op
 * Numbers.zipBuffer    avgt    7  111.581 ± 3.942  us/op
 * Numbers.zipOpto      avgt    7  267.234 ± 2.036  us/op
 * Numbers.zipRx        avgt    7  116.790 ± 5.875  us/op
 * Numbers.zipRxBuffer  avgt    7   85.541 ± 2.157  us/op
 *
 */
@Warmup(iterations = 7, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 7, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
open class Numbers {

    companion object {
        private const val primes = 100
        private const val natural = 1000
    }

    private fun numbers() = flow {
        for (i in 2L..Long.MAX_VALUE) emit(i)
    }

    private fun rxNumbers() =
        Flowable.generate(Callable { 1L }, BiFunction<Long, Emitter<Long>, Long> { state, emitter ->
            val newState = state + 1
            emitter.onNext(newState)
            newState
        })

    private fun generateRxPrimes(): Flowable<Long> = Flowable.generate(Callable { rxNumbers() },
        BiFunction<Flowable<Long>, Emitter<Long>, Flowable<Long>> { state, emitter ->
            // Not the most fair comparison, but here we go
            val prime = state.firstElement().blockingGet()
            emitter.onNext(prime)
            state.filter { it % prime != 0L }
        })

    @Benchmark
    fun zip() = runBlocking {
        val numbers = numbers().take(natural)
        val first = numbers
            .filter { it % 2L != 0L }
            .map { it * it }
        val second = numbers
            .filter { it % 2L == 0L }
            .map { it * it }
        first.zip2(second, 0) { v1, v2 -> v1 + v2 }.filter { it % 3 == 0L }.count()
    }


    @Benchmark
    fun zipBuffer() = runBlocking {
        val numbers = numbers().take(natural)
        val first = numbers
            .filter { it % 2L != 0L }
            .map { it * it }
        val second = numbers
            .filter { it % 2L == 0L }
            .map { it * it }
        first.zip2(second, 128) { v1, v2 -> v1 + v2 }.filter { it % 3 == 0L }.count()
    }

    @Benchmark
    fun zipOpto() = runBlocking {
        val numbers = numbers().take(natural)
        val first = numbers
            .filter { it % 2L != 0L }
            .map { it * it }
        val second = numbers
            .filter { it % 2L == 0L }
            .map { it * it }
        first.zip(second) { v1, v2 -> v1 + v2 }.filter { it % 3 == 0L }.count()
    }

    @Benchmark
    fun zipRxBuffer() {
        val numbers = rxNumbers().take(natural.toLong())
        val first = numbers
            .filter { it % 2L != 0L }
            .map { it * it }
        val second = numbers
            .filter { it % 2L == 0L }
            .map { it * it }
        first.zipWith(second, BiFunction<Long, Long, Long> { v1, v2 -> v1 + v2 }, false, 128).filter { it % 3 == 0L }.count()
            .blockingGet()
    }

    @Benchmark
    fun zipRx() {
        val numbers = rxNumbers().take(natural.toLong())
        val first = numbers
            .filter { it % 2L != 0L }
            .map { it * it }
        val second = numbers
            .filter { it % 2L == 0L }
            .map { it * it }
        first.zipWith(second, BiFunction<Long, Long, Long> { v1, v2 -> v1 + v2 }, false, 1).filter { it % 3 == 0L }.count()
            .blockingGet()
    }
}
