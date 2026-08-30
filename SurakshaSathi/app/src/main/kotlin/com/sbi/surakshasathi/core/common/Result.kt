package com.sbi.surakshasathi.core.common

/**
 * Sealed wrapper for all data operations across the Clean Architecture layers.
 *
 * Usage:
 * ```kotlin
 * when (val result = repo.fetch()) {
 *     is Result.Success -> render(result.data)
 *     is Result.Error   -> showError(result.error)
 *     is Result.Loading -> showLoader()
 * }
 * ```
 *
 * IMPORTANT: Domain and data layers return [Result]; ViewModels map to UiState.
 * Raw exceptions NEVER reach the presentation layer.
 */
sealed class Result<out T> {
    /** Operation completed successfully. [data] is the produced value. */
    data class Success<T>(val data: T) : Result<T>()

    /** Operation failed with a typed [AppError]. */
    data class Error(val error: AppError) : Result<Nothing>()

    /** Operation is in progress (useful for one-shot flows). */
    data object Loading : Result<Nothing>()

    val isSuccess get() = this is Success
    val isError get() = this is Error
    val isLoading get() = this is Loading
}

/** Transforms the data inside [Result.Success], leaving errors untouched. */
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> =
    when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Error -> this
        is Result.Loading -> this
    }

/** Returns the success value or null. */
fun <T> Result<T>.getOrNull(): T? = (this as? Result.Success)?.data

/** Returns the success value or throws the embedded error. */
fun <T> Result<T>.getOrThrow(): T =
    when (this) {
        is Result.Success -> data
        is Result.Error -> throw error.toException()
        is Result.Loading -> error("Result is still Loading")
    }

/** Executes [block] only when [Result.Success]. */
inline fun <T> Result<T>.onSuccess(block: (T) -> Unit): Result<T> {
    if (this is Result.Success) block(data)
    return this
}

/** Executes [block] only when [Result.Error]. */
inline fun <T> Result<T>.onError(block: (AppError) -> Unit): Result<T> {
    if (this is Result.Error) block(error)
    return this
}

/**
 * Safely wraps a suspending call into [Result], mapping any [Throwable] to [AppError].
 */
suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
    try {
        Result.Success(block())
    } catch (e: Exception) {
        Result.Error(AppError.fromException(e))
    }
