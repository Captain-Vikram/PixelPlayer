package com.theveloper.pixelplay.data.stream

/**
 * Common interface contract for all local HTTP streaming proxy servers in PixelPlayer.
 *
 * This interface is the modularization seam: the main app depends only on this
 * lightweight interface. The actual Ktor/CIO server implementations live in separate
 * modules and are loaded dynamically at runtime via DexClassLoader or AAR extraction.
 *
 * # Contract Rules for Implementors
 * 1. All implementations MUST be idempotent — calling [start] or [startIfNeeded]
 *    multiple times must not start duplicate servers.
 * 2. Proxy URLs returned by [getProxyUrl] must always use `http://127.0.0.1:{port}/...`
 *    and must be valid only when [isReady] returns true.
 * 3. [stop] must fully release the port and reset state so the proxy can be restarted.
 * 4. Implementations must be thread-safe. [isReady] and [getProxyUrl] may be called
 *    from any thread.
 *
 * # Fail-Proof Loading Rules (from Section 5 of the Modularization Blueprint)
 * - When loading a native `.so` dependency dynamically, always load transitive
 *   dependencies (e.g. libcrypto.so, libssl.so) BEFORE loading the main library.
 * - Always sanity-check file size before calling System.load() to prevent SIGSEGV
 *   crashes caused by partial/corrupted downloads.
 *
 * @see CloudStreamProxy the existing abstract base for OkHttp-proxying cloud services.
 */
interface StreamProxy {

    /**
     * Returns true if the local HTTP server is running and has bound to a port.
     * Safe to call from any thread.
     */
    fun isReady(): Boolean

    /**
     * Starts the local HTTP server. If already started, this call is a no-op.
     * Non-blocking — the server starts asynchronously on an IO dispatcher.
     */
    fun start()

    /**
     * Starts the server only if it is not already running. Idempotent.
     */
    fun startIfNeeded()

    /**
     * Stops the server, releases the bound port, and resets all internal state.
     * After calling stop(), [isReady] returns false and [start] may be called again.
     */
    fun stop()

    /**
     * Suspends until the server is ready (i.e. [isReady] returns true) or until
     * [timeoutMs] milliseconds have elapsed.
     *
     * @return true if the server became ready within the timeout, false otherwise.
     */
    suspend fun awaitReady(timeoutMs: Long = 10_000L): Boolean

    /**
     * Calls [startIfNeeded] and then [awaitReady]. Convenience for callers that need
     * a guarantee the server is running before making a request.
     *
     * @return true if the server became ready within the timeout, false otherwise.
     */
    suspend fun ensureReady(timeoutMs: Long = 10_000L): Boolean
}
