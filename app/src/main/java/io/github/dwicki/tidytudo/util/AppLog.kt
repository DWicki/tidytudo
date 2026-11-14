package io.github.dwicki.tidytudo.util

import android.util.Log
import io.github.dwicki.tidytudo.BuildConfig

/** The only logger in the app. Every message is redacted; debug lines are dropped in release builds. */
object AppLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, Redactor.redact(message))
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        Log.w(tag, Redactor.redact(message + describe(error)))
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, Redactor.redact(message + describe(error)))
    }

    /** Stack traces can contain URLs in exception messages, so only class and redacted message are logged. */
    private fun describe(error: Throwable?): String =
        if (error == null) "" else " (${error.javaClass.simpleName}: ${error.message.orEmpty()})"
}
