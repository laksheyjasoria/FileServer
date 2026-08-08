package com.app.core.exception;

import com.app.core.response.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(AppException.class)
        public ResponseEntity<ErrorResponse> handleAppException(AppException ex) {

                ErrorCode code = ex.getErrorCode();
                String message = ex.getMessage();
                if (message == null || message.isBlank()) {
                        message = code.getMessage();
                }

                return ResponseEntity
                                .status(code.getStatus())
                                .body(new ErrorResponse(
                                                code.getStatus().value(),
                                                code.name(),
                                                message));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {

                String msg = ex.getBindingResult()
                                .getFieldErrors()
                                .get(0)
                                .getDefaultMessage();

                return ResponseEntity
                                .badRequest()
                                .body(new ErrorResponse(
                                                400,
                                                "VALIDATION_ERROR",
                                                msg));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {

                String message = ex.getMessage();
                if (message == null || message.isBlank()) {
                        message = "Something went wrong";
                }

                return ResponseEntity
                                .internalServerError()
                                .body(new ErrorResponse(
                                                500,
                                                "INTERNAL_ERROR",
                                                message));
        }
}