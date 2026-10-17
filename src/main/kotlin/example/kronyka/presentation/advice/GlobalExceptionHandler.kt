package example.kronyka.presentation.advice

import example.kronyka.domain.model.AppException
import example.kronyka.domain.model.AuctionCancellationException
import example.kronyka.domain.model.AuctionException
import example.kronyka.domain.model.AuctionNotFoundException
import example.kronyka.domain.model.BidNotFoundException
import example.kronyka.domain.model.InvalidAuctionStateException
import example.kronyka.domain.model.InvalidAuctionTimeException
import example.kronyka.domain.model.InvalidAuctionTypeException
import example.kronyka.domain.model.InvalidDepositException
import example.kronyka.domain.model.InvalidReservePriceException
import example.kronyka.domain.model.RevealMismatchException
import example.kronyka.domain.model.UnauthorizedAuctionActionException
import example.kronyka.presentation.dto.ApiResponse
import example.kronyka.presentation.dto.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(AppException::class)
    fun handleAppException(
        ex: AppException
    ): ResponseEntity<ApiResponse<Unit>> {
        val errorCode = ex.errorCode
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

    @ExceptionHandler(AuctionException::class)
    fun handleAuctionException(
        ex: AuctionException
    ): ResponseEntity<ApiResponse<Unit>> {
        val errorCode = when (ex) {
            is AuctionCancellationException -> ErrorCode.AUCTION_CANCELLATION_FAILED
            is AuctionNotFoundException -> ErrorCode.AUCTION_NOT_FOUND
            is BidNotFoundException -> ErrorCode.BID_NOT_FOUND
            is InvalidAuctionStateException -> ErrorCode.INVALID_AUCTION_STATE
            is InvalidAuctionTimeException -> ErrorCode.INVALID_AUCTION_TIME
            is InvalidDepositException -> ErrorCode.INVALID_DEPOSIT
            is InvalidReservePriceException -> ErrorCode.INVALID_RESERVE_PRICE
            is RevealMismatchException -> ErrorCode.REVEAL_MISMATCH
            is UnauthorizedAuctionActionException -> ErrorCode.UNAUTHORIZED_ACTION
            is InvalidAuctionTypeException -> ErrorCode.INVALID_AUCTION_TYPE
        }
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
