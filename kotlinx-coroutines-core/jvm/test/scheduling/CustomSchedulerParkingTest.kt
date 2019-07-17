package kotlinx.coroutines.scheduling

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.lang.IllegalStateException
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.*
import kotlin.coroutines.*
import kotlin.test.*

class CustomSchedulerParkingTest {
    private val channel = Channel<String>(1)
    private lateinit var scheduler: ExperimentalCoroutineDispatcher

    @AfterTest
    fun shutdown() {
        channel.cancel()
        scheduler.close()
    }

    @Test
    fun testWorkProducer() = runBlocking {
        scheduler = ExperimentalCoroutineDispatcher(corePoolSize = 1, parking = WorkProducerParking(channel))

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

    @Test
    fun testSelector() {
        val selector = SelectorParking()
        scheduler = ExperimentalCoroutineDispatcher(corePoolSize = 1, parking = selector)
        val serverAddress = CompletableDeferred<SocketAddress>()

        runBlocking(scheduler + selector) {
            val server = ServerSocketChannel.open()!!
            val serverJob = launch {
                server.bind(InetSocketAddress(0), 5)
                serverAddress.complete(server.localAddress)
                val serverSubject = SelectionSubject(server)

                while (isActive) {
                    val client = serverSubject.accept()
                    launch {
                        println("Connected client")
                        val bb = ByteBuffer.wrap("Hello, World!\r\n".toByteArray())
                        while (bb.hasRemaining()) {
                            client.write(bb)
                        }
                    }.invokeOnCompletion {
                        client.channel.close()
                    }
                }
            }.apply {
                invokeOnCompletion {
                    server.close()
                }
            }

            launch {
                repeat(5) {
                    val socket = SelectionSubject(SocketChannel.open())
                    try {
                        socket.connect(serverAddress.await())
                        val bb = ByteBuffer.allocate(8192)
                        do {
                            if (socket.read(bb) == -1) break
                        } while (bb.hasRemaining())
                        bb.flip()
                        assertEquals("Hello, World!\r\n", String(bb.array(), bb.arrayOffset(), bb.remaining()))
                        println("Got $it")
                    } finally {
                        socket.channel.close()
                    }
                }
            }.invokeOnCompletion {
                serverJob.cancel()
            }
        }
    }

    private class WorkProducerParking(
        private val channel: SendChannel<String>,
        private val maxCount: Int = 3
    ) : Parking<Thread> {
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

    private suspend fun SelectionSubject.read(dst: ByteBuffer): Int {
        val rc = (channel as ReadableByteChannel).read(dst)
        if (rc > 0 || rc == -1 || !dst.hasRemaining()) return rc
        return readSuspend(dst)
    }

    private suspend fun SelectionSubject.readSuspend(dst: ByteBuffer): Int {
        do {
            selectRead()
            val rc = (channel as ReadableByteChannel).read(dst)
            if (rc > 0) return rc
        } while (channel.isOpen)

        return -1
    }

    private suspend fun SelectionSubject.write(src: ByteBuffer): Int {
        val rc = (channel as WritableByteChannel).write(src)
        if (rc > 0 || rc == -1 || !src.hasRemaining()) return rc
        return writeSuspend(src)
    }

    private suspend fun SelectionSubject.writeSuspend(src: ByteBuffer): Int {
        do {
            selectWrite()
            val rc = (channel as WritableByteChannel).write(src)
            if (rc > 0) return rc
        } while (channel.isOpen)

        throw ClosedChannelException()
    }

    private suspend fun SelectionSubject.connect(address: SocketAddress) {
        val channel = channel as SocketChannel
        if (channel.connect(address)) {
            return
        }

        return connectSuspend(channel)
    }

    private suspend fun SelectionSubject.connectSuspend(channel: SocketChannel) {
        do {
            selectConnect()
            if (channel.finishConnect()) return
        } while (true)
    }

    private suspend fun SelectionSubject.accept(): SelectionSubject {
        val channel = channel as ServerSocketChannel
        val client = channel.accept()
        if (client != null) return SelectionSubject(client)
        return acceptSuspend()
    }

    private suspend fun SelectionSubject.acceptSuspend(): SelectionSubject {
        do {
            selectAccept()
            val channel = channel as ServerSocketChannel
            val client = channel.accept()
            if (client != null) return SelectionSubject(client)
        } while (true)
    }

    private suspend fun SelectionSubject.selectRead(): Unit = suspendCancellableCoroutine { c ->
        interestSetup(c) { readOp = it }
    }

    private suspend fun SelectionSubject.selectWrite(): Unit = suspendCancellableCoroutine { c ->
        interestSetup(c) { writeOp = it }
    }

    private suspend fun SelectionSubject.selectConnect(): Unit = suspendCancellableCoroutine { c ->
        interestSetup(c) { connectOp = it }
    }

    private suspend fun SelectionSubject.selectAccept(): Unit = suspendCancellableCoroutine { c ->
        interestSetup(c) { acceptOp = it }
    }

    private fun SelectionSubject.interestSetup(
        continuation: CancellableContinuation<Unit>,
        setter: (Continuation<Unit>?) -> Unit
    ) {
        setter(continuation)
        continuation.invokeOnCancellation {
            setter(null)
            continuation.context[SelectorKey]!!.interest(this)
        }

        try {
            continuation.context[SelectorKey]!!.interest(this)
        } catch (t: Throwable) {
            continuation.resumeWithException(t)
        }
    }

    private class SelectionSubject(val channel: SelectableChannel) {
        var key: SelectionKey? = null

        var readOp: Continuation<Unit>? = null
        var writeOp: Continuation<Unit>? = null
        var acceptOp: Continuation<Unit>? = null
        var connectOp: Continuation<Unit>? = null

        init {
            channel.configureBlocking(false)
        }

        fun exception(): Throwable {
            return when {
                !channel.isOpen -> ClosedChannelException()
                else -> IllegalStateException("Channel selection aborted")
            }
        }

        fun handleSelected() {
            val key = key!!

            if (!key.isValid) {
                this.key = null
                readOp?.let { readOp = null; it.resumeWithException(exception()) }
                writeOp?.let { writeOp = null; it.resumeWithException(exception()) }
                acceptOp?.let { acceptOp = null; it.resumeWithException(exception()) }
                connectOp?.let { connectOp = null; it.resumeWithException(exception()) }
                return
            }

            val readyOps = key.readyOps()

            if (readyOps and SelectionKey.OP_READ != 0) {
                readOp?.let { readOp = null; it.resume(Unit) }
            }

            if (readyOps and SelectionKey.OP_WRITE != 0) {
                writeOp?.let { writeOp = null; it.resume(Unit) }
            }

            if (readyOps and SelectionKey.OP_CONNECT != 0) {
                connectOp?.let { connectOp = null; it.resume(Unit) }
            }

            if (readyOps and SelectionKey.OP_ACCEPT != 0) {
                acceptOp?.let { acceptOp = null; it.resume(Unit) }
            }

            key.interestOps(interest())
        }

        fun interest(): Int {
            var newInterest = 0

            if (readOp != null) {
                newInterest = newInterest or SelectionKey.OP_READ
            }
            if (writeOp != null) {
                newInterest = newInterest or SelectionKey.OP_WRITE
            }
            if (connectOp != null) {
                newInterest = newInterest or SelectionKey.OP_CONNECT
            }
            if (acceptOp != null) {
                newInterest = newInterest or SelectionKey.OP_ACCEPT
            }

            return newInterest
        }
    }

    private object SelectorKey : CoroutineContext.Key<SelectorParking>

    private class SelectorParking : Parking<Thread>, CoroutineContext.Element {
        override val key: CoroutineContext.Key<*>
            get() = SelectorKey

        private val selector = Selector.open()!!

        override fun park(worker: Thread, nanoseconds: Long) {
            println("Selecting...")
            selector.select(TimeUnit.MILLISECONDS.convert(nanoseconds, TimeUnit.NANOSECONDS))
            selector.selectedKeys().forEach { key ->
                val subject = key.attachment() as SelectionSubject
                check(subject.key === key)
                subject.handleSelected()
            }
            selector.selectedKeys().clear()
        }

        override fun unpark(worker: Thread) {
            selector.wakeup()
        }

        override fun workerStopped(worker: Thread) {
        }

        fun interest(subject: SelectionSubject) {
            subject.key?.let { key ->
                if (key.isValid) {
                    key.interestOps(subject.interest())
                    return
                }
            }

            subject.key = subject.channel.register(selector, subject.interest(), subject)
        }

        fun close() {
            selector.close()
        }
    }
}