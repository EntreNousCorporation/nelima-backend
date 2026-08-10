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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.Locale;
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
        // Le nom de classe et le détail de l'analyseur JSON partaient au client. Ils restent au
        // journal ; l'appelant reçoit un message clair.
        log.warn("Malformed request body: {}:{}", rootCause.getClass().getName(), rootCause.getMessage());

        return ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .debugMessage("La requête est mal formée.")
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

    /**
     * Une faute de forme du client est un 400, jamais une panne du serveur.
     *
     * <p>Ces deux exceptions n'étaient traitées nulle part et tombaient sur le
     * {@code @ExceptionHandler(Exception.class)}, donc en <strong>500</strong> : un identifiant qui
     * n'est pas un UUID, une date illisible, un paramètre obligatoire omis. Elles n'héritent ni de
     * {@code BindException} ni d'{@code IllegalArgumentException} — {@code TypeMismatchException}
     * descend de {@code BeansException} — d'où le trou, invisible à la lecture des handlers déjà
     * présents.
     *
     * <p>Un 500 n'est pas qu'un mauvais code : il déclenche les alertes, il gonfle les journaux
     * d'incidents, et il fait chercher une panne là où il n'y a qu'une requête mal formée.
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleMalformedRequest(Exception exception, HttpServletRequest httpServletRequest) {
        log.warn("Requête mal formée: {}", exception.getLocalizedMessage());
        return ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .path(httpServletRequest.getRequestURI())
                .debugMessage("La requête est mal formée.")
                .build();
    }

    /**
     * Une URL inconnue est un 404.
     *
     * <p>Elle rendait un 500 : {@code NoResourceFoundException} n'était pas traitée, et
     * {@code ExceptionHandlerExceptionResolver} passe avant le résolveur par défaut de Spring, qui
     * l'aurait pourtant correctement rendue.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleUnknownRoute(NoResourceFoundException exception, HttpServletRequest httpServletRequest) {
        return ApiError.builder()
                .status(HttpStatus.NOT_FOUND)
                .path(httpServletRequest.getRequestURI())
                .debugMessage("La ressource demandée est introuvable.")
                .build();
    }

    @ExceptionHandler({NotFoundException.class, UsernameNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFoundException(Exception exception, HttpServletRequest httpServletRequest) {
        // Le détail brut (« Cannot find user with email … », identifiants techniques) reste au
        // journal ; l'appelant ne reçoit qu'un message générique, en français et sans donnée sensible.
        log.warn("Not found: {}", exception.getLocalizedMessage());
        return ApiError.builder()
                .status(HttpStatus.NOT_FOUND)
                .debugMessage("La ressource demandée est introuvable.")
                .path(httpServletRequest.getRequestURI())
                .build();
    }


    /**
     * Les exceptions métier ({@link BadRequestException}) portent des messages rédigés pour
     * l'utilisateur, en français : ils sont transmis tels quels.
     */
    @ExceptionHandler({BadRequestException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleBadRequestException(BadRequestException exception, HttpServletRequest httpServletRequest) {

        return ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .debugMessage(exception.getLocalizedMessage())
                .path(httpServletRequest.getRequestURI())
                .build();
    }

    /**
     * Argument invalide ou identifiants erronés : messages internes, souvent techniques. On ne les
     * expose pas — un libellé français générique, le détail au journal.
     */
    @ExceptionHandler({IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleIllegalArgument(Exception exception, HttpServletRequest httpServletRequest) {
        log.warn("Invalid request: {}", exception.getLocalizedMessage());
        return ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .debugMessage("La requête n'a pas pu être traitée.")
                .path(httpServletRequest.getRequestURI())
                .build();
    }

    @ExceptionHandler({BadCredentialsException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleBadCredentials(Exception exception, HttpServletRequest httpServletRequest) {
        return ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .debugMessage("Identifiant ou mot de passe incorrect.")
                .path(httpServletRequest.getRequestURI())
                .build();
    }


    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ApiError processError(HttpMediaTypeNotSupportedException exception, HttpServletRequest httpServletRequest) {

        return ApiError.builder()
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .debugMessage("Le format du contenu envoyé n'est pas pris en charge.")
                .path(httpServletRequest.getRequestURI())
                .build();
    }

    /**
     * Le client demande un type que la route ne produit pas.
     *
     * <p>Sans ce traitement, l'erreur tombait dans le fourre-tout et sortait en 500 : un client qui
     * demandait du JSON sur le PDF d'un reçu recevait « Internal server error », ce qui envoie
     * chercher une panne serveur là où seul l'en-tête {@code Accept} est en cause.
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    @ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
    public ApiError processError(HttpMediaTypeNotAcceptableException exception,
                                 HttpServletRequest httpServletRequest) {

        return ApiError.builder()
                .status(HttpStatus.NOT_ACCEPTABLE)
                .debugMessage("Le format de réponse demandé n'est pas disponible.")
                .path(httpServletRequest.getRequestURI())
                .build();
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiError processError(HttpRequestMethodNotSupportedException exception, HttpServletRequest httpServletRequest) {

        return ApiError.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .debugMessage("Cette action n'est pas autorisée sur cette ressource.")
                .path(httpServletRequest.getRequestURI())
                .build();
    }

    @ExceptionHandler({UnAuthenticatedUserException.class,
            ExpiredJwtException.class,
            MalformedJwtException.class,
            /*
             * Une signature invalide est un refus, pas un demi-succès.
             *
             * <p>Elle rendait `206 Partial Content` — un code de la famille 2xx. Tout client qui
             * teste `response.ok` (le défaut de `fetch`, de Dio et de la plupart des enveloppes
             * HTTP) prenait donc un jeton forgé pour un appel réussi, puis tentait de lire l'objet
             * attendu dans un corps d'erreur. Côté mobile, cela retombait sur l'écran muet.
             *
             * <p>Le chemin s'ouvre sur un jeton bien formé signé d'une AUTRE clé — rotation du
             * secret, ou jeton d'un environnement de développement pointé sur la production. Il
             * rejoint donc les autres refus d'authentification : 401.
             */
            SignatureException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError handleUnauthorized(Exception exception, HttpServletRequest httpServletRequest) {
        log.warn("Unauthorized: {}", exception.getLocalizedMessage());
        return ApiError.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .path(httpServletRequest.getRequestURI())
                .debugMessage("Votre session a expiré. Veuillez vous reconnecter.")
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
                .debugMessage("Une erreur est survenue. Veuillez réessayer plus tard.")
                .build();
    }

    @ExceptionHandler({AccessDeniedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError processForbiddenError(Exception exception, HttpServletRequest httpServletRequest) {
        // Le motif exact (élève, compte, permission visés — souvent avec un identifiant) reste au
        // journal : le divulguer renseignerait sur des ressources auxquelles l'appelant n'a pas droit.
        log.warn("Forbidden: {}", exception.getLocalizedMessage());
        return ApiError.builder()
                .status(HttpStatus.FORBIDDEN)
                .path(httpServletRequest.getRequestURI())
                .debugMessage("Vous n'êtes pas autorisé à effectuer cette action.")
                .build();
    }
}
