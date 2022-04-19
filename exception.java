package mn.mbank.reactive.common.exception;

import mn.mbank.reactive.common.model.MResponse;
import mn.mbank.reactive.common.model.Microservice;
import mn.mbank.reactive.common.util.MLogger;
import org.springframework.boot.autoconfigure.web.ResourceProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
@Component
@Order(-2)
@Profile({"dev","test","uat"})
public class MGExceptionHandler extends AbstractErrorWebExceptionHandler {

    private final MLogger log;
    private final Microservice mic;

    MGExceptionHandler(
            ErrorAttributes errorAttributes,
            ResourceProperties resourceProperties,
            ApplicationContext applicationContext,
            ServerCodecConfigurer configurer,
            MLogger log,
            Microservice mic
    ) {
        super(errorAttributes, resourceProperties, applicationContext);
        this.setMessageWriters(configurer.getWriters());
        this.log = log;
        this.mic = mic;
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(),this::formatErrorResponse);
    }

    @NonNull
    private Mono<ServerResponse> formatErrorResponse(ServerRequest serverRequest) {

        Map<String,Object> errorAttrbutes = this.getErrorAttributes(serverRequest,
                ErrorAttributeOptions.of(ErrorAttributeOptions.Include.STACK_TRACE, ErrorAttributeOptions.Include.MESSAGE));

        Throwable error = this.getError(serverRequest);

        if (error instanceof IllegalArgumentException) {
            log.error("Буруу хүсэлт! " + serverRequest.uri().getPath(), error);
            return ServerResponse.status(200)
                   .body(BodyInserters.fromValue(new MResponse<>("4000","Буруу хүсэлт"+mic.micname()).error(error)));
        }

        if (error instanceof WebExchangeBindException){
            final WebExchangeBindException e = (WebExchangeBindException)error;
            Map<String, Object> errors = new HashMap<>();
            e.getBindingResult().getAllErrors().forEach(msg->{
                String fieldName = ((FieldError) msg).getField();
                String errorMessage = msg.getDefaultMessage();
                errors.put(fieldName, errorMessage);
            });
            return ServerResponse.status(200)
                    .body(BodyInserters.fromValue(new MResponse<>("4000","Буруу хүсэлт!"+mic.micname()).addj(errors)));
        }

        if (error instanceof ResponseStatusException) {
            log.error("Буруу хүсэлт! " + serverRequest.uri().getPath(), error);
            final ResponseStatusException e = (ResponseStatusException) error;

            return ServerResponse.status(200)
                    .body(BodyInserters.fromValue(new MResponse<>("4000","Буруу хүсэлт:"+e.getReason()+mic.micname()).error(error)));
        }

        if (error instanceof MException) {
            log.error(error.getMessage() + " [" + serverRequest.uri().getPath()+"]", error.getCause());
            final MException e = (MException) error;
            return ServerResponse.status(200)
                    .body(BodyInserters
                            .fromValue(MResponse.ss(e.msgstat()).build().error(e.getCause())));
        }

        log.error("Тооцоогүй алдаа! " + serverRequest.uri().getPath(), error);

        return ServerResponse.status(200)
                .body(BodyInserters
                        .fromValue(new MResponse<>("5001","Өө яачав!"+mic.micname()).error(error)));

    }

}
