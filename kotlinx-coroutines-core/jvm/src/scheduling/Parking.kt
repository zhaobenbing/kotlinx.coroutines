package kotlinx.coroutines.scheduling

import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import java.util.concurrent.locks.*

/**
 * Provides worker threads parking functionality.
 */
@InternalCoroutinesApi
interface Parking<in W : Any> {
    /**
     * Parks the [worker] thread at least to the specified time in [nanoseconds] unless [unpark] is called or
     * the underlying implementation does unpark it due to specific conditions.
     * Depending on the implementation it could suspend the thread, run helper tasks or process event loops.
     * Due to the asynchronous [unpark] nature, the thread could be spuriously awakened
     * before the specified [nanoseconds] elapsed.
     */
    fun park(worker: W, nanoseconds: Long)

    /**
     * Unparks the [worker] thread. Depending on the underlying implementation, it could resume the thread immediately
     * or post a message to resume it in the future. Due to possible race between park and unpark, an unpark request
     * may affect the future park attempt. It is not specified whether several unparks grouped into a single
     * or the corresponding number of parking attempts will be aborted.
     */
    fun unpark(worker: W)

    /**
     * Should be called once [worker] is about to stop or already stopped.
     * After calling this function, neither [park] nor [unpark] should be invoked for the [worker].
     */
    fun workerStopped(worker: Thread)
}

/**
 * The default [Parking] implementation based on JDK [LockSupport].
 */
@InternalCoroutinesApi
object LockSupportParking : Parking<Thread> {
    override fun park(worker: Thread, nanoseconds: Long) {
        LockSupport.parkNanos(nanoseconds)
    }

    override fun unpark(worker: Thread) {
        LockSupport.unpark(worker)
    }

    override fun workerStopped(worker: Thread) {
    }
}

@InternalCoroutinesApi
internal class EventLoopParking(
    private val eventLoop: EventLoop,
    private val timeSource: TimeSource,
    private val delegate: Parking<Thread>
) : Parking<Thread> {

    private val workerCounters = ConcurrentHashMap(IdentityHashMap<Thread, AtomicInteger>(1024))

    override fun park(worker: Thread, nanoseconds: Long) {
        require(worker === Thread.currentThread())

        val counter = counterFor(worker)

        val startTime = timeSource.nanoTime()
        loop@ do {
            val timeToWait = eventLoop.processNextEvent()

            val remaining = nanoseconds - (timeSource.nanoTime() - startTime)
            if (remaining <= 0) break

            when {
                remaining <= 0 -> break@loop
                counter.get() > 0 -> {
                    counter.set(0)
                    break@loop
                }
                timeToWait <= 0 -> continue@loop
                else -> delegate.park(worker, minOf(remaining, timeToWait))
            }
        } while (true)
    }

    override fun unpark(worker: Thread) {
        counterFor(worker).incrementAndGet()
        delegate.unpark(worker)
    }

    override fun workerStopped(worker: Thread) {
        workerCounters.remove(worker)
    }

    private fun counterFor(worker: Thread): AtomicInteger {
        val existing = workerCounters[worker]
        if (existing != null) return existing

        val newCounter = AtomicInteger()
        workerCounters.putIfAbsent(worker, newCounter)?.let { return it }

        return newCounter
    }
}
