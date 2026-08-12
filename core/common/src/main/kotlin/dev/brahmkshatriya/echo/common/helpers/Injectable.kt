package dev.brahmkshatriya.echo.common.helpers

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow

class Injectable<T>(
    private val getter: () -> T,
    private var injections: List<suspend T.() -> Unit>
) {

    private val _instanceFlow = kotlinx.coroutines.flow.MutableStateFlow<Result<T>?>(null)
    val instanceFlow: kotlinx.coroutines.flow.StateFlow<Result<T>?> = _instanceFlow.asStateFlow()

    val data = lazy {
        runCatching { getter() }.also {
            _instanceFlow.value = it
        }
    }
    private val mutex = Mutex()
    val value: T?
        get() = data.value.getOrNull()

    private val lastInjectionSequence = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val completedInjections = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.flow.MutableStateFlow<Boolean>>()

    private fun stateFlowFor(id: String): kotlinx.coroutines.flow.MutableStateFlow<Boolean> {
        return completedInjections.getOrPut(id) { kotlinx.coroutines.flow.MutableStateFlow(false) }
    }

    fun rearmNamedInjection(id: String) {
        stateFlowFor(id).value = false
    }

    fun setNamedInjectionComplete(id: String) {
        stateFlowFor(id).value = true
    }

    suspend fun awaitNamedInjection(id: String) {
        if (!data.isInitialized()) {
            value()
        }
        stateFlowFor(id).first { it }
    }

    suspend fun value() = runCatching {
        mutex.withLock {
            val t = data.value.getOrThrow()
            injections.forEach { it(t) }
            injections = emptyList()
            injectionsMap.values.forEach { it(t) }
            injectionsMap.clear()
            t
        }
    }

    private val injectionsMap = mutableMapOf<String, suspend T.() -> Unit>()
    suspend fun injectOrRun(id: String, seq: Long, block: suspend T.() -> Unit) {
        mutex.withLock {
            val lastSeq = lastInjectionSequence[id] ?: -1L
            if (seq < lastSeq) {
                // Out-of-order execution detected. Ignore.
                return
            }
            lastInjectionSequence[id] = seq

            val flow = stateFlowFor(id)
            if (data.isInitialized()) {
                flow.value = false
                try {
                    data.value.getOrThrow().block()
                } finally {
                    flow.value = true
                }
            } else {
                flow.value = false
                injectionsMap[id] = {
                    try {
                        block()
                    } finally {
                        flow.value = true
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <R> casted() = run {
        injections = injections + listOf { this as R }
        this as Injectable<R>
    }
}