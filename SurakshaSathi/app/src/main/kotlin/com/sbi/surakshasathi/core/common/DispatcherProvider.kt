package com.sbi.surakshasathi.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Abstraction over [CoroutineDispatcher] for testability.
 *
 * Inject this interface instead of using [Dispatchers] directly so that
 * unit tests can substitute [TestDispatcherProvider] to control coroutine timing.
 *
 * Hilt binding: [com.sbi.surakshasathi.core.di.DispatchersModule]
 */
interface DispatcherProvider {
    /** Main thread — UI updates only. Never block here. */
    val main: CoroutineDispatcher

    /** Optimised for disk/network I/O (Room, Retrofit, DataStore). */
    val io: CoroutineDispatcher

    /** Optimised for CPU-intensive work (hashing, ML inference, parsing). */
    val default: CoroutineDispatcher

    /** Unconfined — for testing only; avoid in production. */
    val unconfined: CoroutineDispatcher
}

/** Production implementation backed by [Dispatchers]. */
class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}
