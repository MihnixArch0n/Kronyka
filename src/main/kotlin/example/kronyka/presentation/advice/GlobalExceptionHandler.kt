package example.kronyka.presentation.advice

import example.kronyka.domain.model.BadCredentialsException
import example.kronyka.domain.model.UserAlreadyExistsException
import example.kronyka.presentation.dto.ApiResponse
import example.kronyka.presentation.dto.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class AppException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message
) : RuntimeException(message)

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(AppException::class)
    fun handleAppException(
        ex: AppException
    ): ResponseEntity<ApiResponse<Unit>> {
        val errorCode = ex.errorCode
        val response = ApiResponse.error<Unit>(errorCode, ex.message)
        return ResponseEntity.status(errorCode.httpStatus).body(response)
    }

    @ExceptionHandler(UserAlreadyExistsException::class)
    fun handleUserAlreadyExists(
        ex: UserAlreadyExistsException
    ): ResponseEntity<ApiResponse<Unit>> {
        val errorCode = ErrorCode.USER_ALREADY_EXISTS
        val response = ApiResponse.error<Unit>(errorCode, ex.message ?: errorCode.message)
        return ResponseEntity.status(errorCode.httpStatus).body(response)
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(
        ex: DataIntegrityViolationException
    ): ResponseEntity<ApiResponse<Unit>> {
        val errorCode = ErrorCode.DATA_INTEGRITY_VIOLATION
        val response = ApiResponse.error<Unit>(errorCode, errorCode.message)
        return ResponseEntity.status(errorCode.httpStatus).body(response)
    }

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(
        ex: BadCredentialsException
    ): ResponseEntity<ApiResponse<Unit>> {
        val errorCode = ErrorCode.INVALID_CREDENTIALS
        val response = ApiResponse.error<Unit>(errorCode, ex.message ?: errorCode.message)
        return ResponseEntity.status(errorCode.httpStatus).body(response)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException
    ): ResponseEntity<ApiResponse<Unit>> {
        val errorCode = ErrorCode.VALIDATION_FAILED
        val message = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        val response = ApiResponse.error<Unit>(errorCode, message)
        return ResponseEntity.status(errorCode.httpStatus).body(response)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException
    ): ResponseEntity<ApiResponse<Unit>> {
        val errorCode = ErrorCode.MALFORMED_JSON
        val message = ex.mostSpecificCause.message ?: errorCode.message
        val response = ApiResponse.error<Unit>(errorCode, message)
        return ResponseEntity.status(errorCode.httpStatus).body(response)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException
    ): ResponseEntity<ApiResponse<Unit>> {
        val errorCode = ErrorCode.BAD_REQUEST
        val response = ApiResponse.error<Unit>(errorCode, ex.message ?: errorCode.message)
        return ResponseEntity.status(errorCode.httpStatus).body(response)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception
    ): ResponseEntity<ApiResponse<Unit>> {
        val errorCode = ErrorCode.UNCATEGORIZED_EXCEPTION
        val response = ApiResponse.error<Unit>(errorCode, ex.message ?: errorCode.message)
        return ResponseEntity.status(errorCode.httpStatus).body(response)
    }
}
