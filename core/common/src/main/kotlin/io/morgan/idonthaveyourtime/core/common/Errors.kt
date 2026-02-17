package io.morgan.idonthaveyourtime.core.common

class ProcessingException(
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
