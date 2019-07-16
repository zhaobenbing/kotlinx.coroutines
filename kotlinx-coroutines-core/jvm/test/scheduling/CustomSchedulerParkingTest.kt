package kotlinx.coroutines.scheduling

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.util.concurrent.atomic.*
import kotlin.test.*

class CustomSchedulerParkingTest {
    private val channel = Channel<String>(1)
    private val scheduler = ExperimentalCoroutineDispatcher(corePoolSize = 1, parking = WorkProducerParking(channel))

    @Test
    fun test() = runBlocking {
        val result = async(scheduler) {
            val collected = ArrayList<String>()

            for (element in channel) {
                collected.add(element)
            }

            collected
        }

        val collected = result.await()

        assertEquals(listOf("value-1", "value-2"), collected)
    }

    private class WorkProducerParking(
        private val channel: SendChannel<String>,
        private val maxCount: Int = 3) : Parking<Thread> {
        private val counter = AtomicInteger()

        override fun park(worker: Thread, nanoseconds: Long) {
            val attempt = counter.incrementAndGet()
            if (attempt >= maxCount) {
                channel.close()
            } else {
                channel.offer("value-$attempt")
            }
        }

        override fun unpark(worker: Thread) {
        }

        override fun workerStopped(worker: Thread) {
        }
    }
}