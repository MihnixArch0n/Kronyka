package example.kronyka.presentation.dto

import com.fasterxml.jackson.annotation.JsonInclude
import kotlinx.serialization.Serializable

@Serializable
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val code: Int = 1000,
    val message: String? = null,
    val result: T? = null
) {
    companion object {
        fun <T> success(result: T? = null): ApiResponse<T> =
            ApiResponse(
                code = 1000,
                message = null,
                result = result
            )


        fun <T> error(errorCode: ErrorCode, message: String = errorCode.message): ApiResponse<T> =
            ApiResponse(
                code = errorCode.code,
                message = message,
                result = null
            )
    }
}

