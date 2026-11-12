package com.apex.ai.core

/**
 * Tiny result type used across the engine so failures are values, not crashes.
 * Deliberately not [kotlin.Result] because we carry a machine-readable code that
 * the UI renders as a status chip instead of a stack trace.
 */
sealed class Outcome<out T> {
    data class Ok<out T>(val value: T) : Outcome<T>()
    data class Err(val message: String, val code: String = "E_UNKNOWN", val cause: Throwable? = null) : Outcome<Nothing>()

    val isOk: Boolean get() = this is Ok
    val isErr: Boolean get() = this is Err

    fun getOrNull(): T? = (this as? Ok)?.value
    fun errorOrNull(): Err? = this as? Err

    inline fun <R> map(transform: (T) -> R): Outcome<R> = when (this) {
        is Ok -> Ok(transform(value))
        is Err -> this
    }

    companion object {
        fun <T> ok(value: T): Outcome<T> = Ok(value)
        fun <T> err(message: String, code: String = "E_UNKNOWN", cause: Throwable? = null): Outcome<T> =
            Err(message, code, cause)
    }
}

inline fun <T> runCatchingOutcome(code: String = "E_UNKNOWN", block: () -> T): Outcome<T> =
    try {
        Outcome.Ok(block())
    } catch (t: Throwable) {
        Outcome.Err(t.message ?: t.javaClass.simpleName, code, t)
    }
