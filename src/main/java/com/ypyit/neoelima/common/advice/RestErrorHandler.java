package com.ypyit.neoelima.common.advice;


import com.ypyit.neoelima.common.advice.error.ApiError;
import com.ypyit.neoelima.common.advice.error.ErrorDetail;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.common.exception.CommonsException;
import com.ypyit.neoelima.common.exception.DuplicateResourceException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.common.exception.UnAuthenticatedUserException;
import com.ypyit.neoelima.common.exception.ValidationException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RestControllerAdvice
public class RestErrorHandler {

    /**
     * Champs dont la valeur refusée ne doit jamais être renvoyée à l'appelant.
     *
     * <p>Une erreur de validation sur un mot de passe renvoyait sa valeur en clair dans la
     * réponse HTTP — et donc potentiellement dans les journaux d'accès, les outils de
     * supervision et l'historique du navigateur. Un simple mot de passe trop long suffisait à
     * l'exposer.
     */
    private static final Set<String> SENSITIVE_FIELDS =
            Set.of("password", "newpassword", "oldpassword", "confirmpassword", "pin", "otp",
                    "secret", "token", "apikey", "webhooksecret");

    private static final String REDACTED = "***";

    private List<ErrorDetail> getErrorDetails(BindingResult bindingResult) {

        Stream<ErrorDetail> fieldErrors = bindingResult.getFieldErrors().stream().map(error -> ErrorDetail.builder()
                .code(error.getCode())
                .defaultMessage(error.getDefaultMessage())
                .field(error.getField())
                .rejectValue(maskIfSensitive(error.getField(), error.getRejectedValue()))
                .build());

        Stream<ErrorDetail> globalErrors = bindingResult.getGlobalErrors().stream().map(error -> ErrorDetail.builder()
                .code(error.getCode())
                .defaultMessage(error.getDefaultMessage())
                .field(error.getObjectName())
                .build());

        return Stream.concat(fieldErrors, globalErrors).collect(Collectors.toList());
    }

    /**
     * Le nom du champ est normalisé avant comparaison : la validation peut le remonter sous des
     * formes variées ({@code password}, {@code changePasswordForm.newPassword}, {@code new_password}),
     * et laisser passer une seule de ces variantes suffirait à réexposer la valeur.
     */
    private static Object maskIfSensitive(String field, Object rejectedValue) {
        if (Objects.isNull(rejectedValue) || Objects.isNull(field)) {
            return rejectedValue;
        }
        String normalized = field.substring(field.lastIndexOf('.') + 1)
                .replace("_", "")
                .toLowerCase(Locale.ROOT);
        return SENSITIVE_FIELDS.contains(normalized) ? REDACTED : rejectedValue;
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError processError(ValidationException exception, HttpServletRequest httpServletRequest) {

        ErrorDetail errorDetail = ErrorDetail.builder()
                .field(exception.getField())
                .defaultMessage(exception.getLocalizedMessage())
                .build();

        return ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .errors(Collections.singletonList(errorDetail))
                .path(httpServletRequest.getRequestURI())
                .build();
    }

    @ExceptionHandler({DuplicateResourceException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError processError(HttpServletRequest httpServletRequest, DuplicateResourceException ex) {

        return ApiError.builder()
                .status(HttpStatus.CONFLICT)
                .debugMessage(ex.getLocalizedMessage())
                .path(httpServletRequest.getRequestURI())
                .build();
    }


    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError processError(HttpMessageNotReadableException exception, HttpServletRequest httpServletRequest) {

        Throwable rootCause = exception.getMostSpecificCause();
        String message = new StringJoiner(":")
                .add(rootCause.getClass().getName())
                .add(rootCause.getMessage()).toString();

        return ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .debugMessage(message)
                .path(httpServletRequest.getRequestURI())
                .build();
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError processError(BindException exception, HttpServletRequest httpServletRequest) {

        List<ErrorDetail> errorDetails = this.getErrorDetails(exception);

        return ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .errors(errorDetails)
                .path(httpServletRequest.getRequestURI())
                .build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError processError(MethodArgumentNotValidException exception, HttpServletRequest httpServletRequest) {

        List<ErrorDetail> errorDetails = this.getErrorDetails(exception);

        return ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .errors(errorDetails)
                .path(httpServletRequest.getRequestURI())
                .build();
    }

    @ExceptionHandler({NotFoundException.class, UsernameNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFoundException(Exception exception, HttpServletRequest httpServletRequest) {

        return ApiError.builder()
                .status(HttpStatus.NOT_FOUND)
                .debugMessage(exception.getLocalizedMessage())
                .path(httpServletRequest.getRequestURI())
                .build();
    }


    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class, BadCredentialsException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleBadRequestException(Exception exception, HttpServletRequest httpServletRequest) {

        return ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .debugMessage(exception.getLocalizedMessage())
                .path(httpServletRequest.getRequestURI())
                .build();
    }


    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ApiError processError(HttpMediaTypeNotSupportedException exception, HttpServletRequest httpServletRequest) {

        String unsupported = "Unsupported content type : " + exception.getContentType();
        String supported = "Supported content type : " + MediaType.toString(exception.getSupportedMediaTypes());

        return ApiError.builder()
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .debugMessage(new StringJoiner(" - ")
                        .add(unsupported).add(supported).toString())
                .path(httpServletRequest.getRequestURI())
                .build();
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiError processError(HttpRequestMethodNotSupportedException exception, HttpServletRequest httpServletRequest) {

        return ApiError.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .debugMessage(exception.getLocalizedMessage())
                .path(httpServletRequest.getRequestURI())
                .build();
    }

    @ExceptionHandler(SignatureException.class)
    @ResponseStatus(HttpStatus.PARTIAL_CONTENT)
    public ApiError processError(SignatureException exception, HttpServletRequest httpServletRequest) {

        return ApiError.builder()
                .status(HttpStatus.PARTIAL_CONTENT)
                .path(httpServletRequest.getRequestURI())
                .debugMessage(exception.getLocalizedMessage())
                .build();
    }

    @ExceptionHandler({UnAuthenticatedUserException.class,
            ExpiredJwtException.class,
            MalformedJwtException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError handleUnauthorized(Exception exception, HttpServletRequest httpServletRequest) {

        return ApiError.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .path(httpServletRequest.getRequestURI())
                .debugMessage(exception.getLocalizedMessage())
                .build();
    }

    @ExceptionHandler({Exception.class,
            CommonsException.class,
            BusinessException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError processError(Exception exception, HttpServletRequest httpServletRequest) {
        log.error("Internal server error {}", exception.getLocalizedMessage());
        return ApiError.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .path(httpServletRequest.getRequestURI())
                .debugMessage("Internal server error")
                .build();
    }

    @ExceptionHandler({AccessDeniedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError processForbiddenError(Exception exception, HttpServletRequest httpServletRequest) {
        log.error("Forbidden error {}", exception.getLocalizedMessage());
        return ApiError.builder()
                .status(HttpStatus.FORBIDDEN)
                .path(httpServletRequest.getRequestURI())
                .debugMessage(exception.getLocalizedMessage())
                .build();
    }
}
