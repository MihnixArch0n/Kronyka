package example.kronyka.domain.model

import example.kronyka.presentation.dto.ErrorCode

open class AppException(
    val errorCode: ErrorCode = ErrorCode.UNCATEGORIZED_EXCEPTION,
    message: String = errorCode.message,
    cause: Throwable? = null
) : RuntimeException(message, cause) {
    constructor(errorCode: ErrorCode) : this(errorCode, errorCode.message, null)
    constructor(errorCode: ErrorCode, message: String) : this(errorCode, message, null)
    constructor(errorCode: ErrorCode, cause: Throwable?) : this(errorCode, errorCode.message, cause)
    constructor(message: String) : this(ErrorCode.UNCATEGORIZED_EXCEPTION, message, null)
    constructor(message: String, cause: Throwable?) : this(ErrorCode.UNCATEGORIZED_EXCEPTION, message, cause)
}

