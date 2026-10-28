package com.accenture.externalapis.demo.client;

import com.accenture.externalapis.demo.config.ExternalServiceProperties;
import com.accenture.externalapis.demo.dto.BookApiResponse;
import com.accenture.externalapis.demo.dto.BookDto;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;


@Component
public class BookWebClientImpl implements BookWebClient{

    private WebClient webClient;

    public BookWebClientImpl(WebClient.Builder builder, ExternalServiceProperties properties) {
        builder.baseUrl(properties.baseUrl());

        if (properties.token() != null && !properties.token().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + properties.token());
        }

        this.webClient = builder.build();
    }

    @Override
    public Mono<BookDto> getBookAsync(Long id) {
        return webClient.get()
                .uri("/books/{id}", id)
                .retrieve()
                .bodyToMono(BookApiResponse.class)
                .switchIfEmpty(Mono.error(new ClientException("Empty response from external API")))
                .timeout(Duration.ofSeconds(3))
                .retryWhen(retryPolicy())
                .map(response -> new BookDto(
                        response.title(),
                        response.author(),
                        response.genre(),
                        response.price()))
                .onErrorMap(WebClientResponseException.class,
                        e -> new ClientException("External API error: " + e.getStatusCode(), e))
                .onErrorMap(WebClientRequestException.class,
                        e -> new ClientException("Connection error: " + e.getMessage(), e))
                .onErrorMap(TimeoutException.class,
                        e -> new ClientException("External API request timed out", e))
                .onErrorMap(e -> !(e instanceof ClientException),
                        e -> new ClientException("Failed to fetch book: " + e.getMessage(), e));
    }

    @Override
    public Flux<BookDto> getAllBooksAsync() {
        return webClient
                .get()
                .uri("/books")
                .retrieve()
                .bodyToFlux(BookApiResponse.class)
                .collectList()
                .timeout(Duration.ofSeconds(3))
                .retryWhen(retryPolicy())
                .flatMapMany(Flux::fromIterable)
                .map(response -> new BookDto(
                        response.title(),
                        response.author(),
                        response.genre(),
                        response.price()))
                .onErrorMap(WebClientResponseException.class,
                        e -> new ClientException("External API error: " + e.getStatusCode(), e))
                .onErrorMap(WebClientRequestException.class,
                        e -> new ClientException("Connection error: " + e.getMessage(), e))
                .onErrorMap(TimeoutException.class,
                        e -> new ClientException("External API request timed out", e))
                .onErrorMap(e -> !(e instanceof ClientException),
                        e -> new ClientException("Failed to fetch book: " + e.getMessage(), e));
    }

    @Override
    public Mono<List<BookDto>> getBooksInParallel(Long id1, Long id2) {
        Mono<BookDto> book1 = getBookAsync(id1);
        Mono<BookDto> book2 = getBookAsync(id2);

        return Mono.zip(book1, book2).map(tuple -> List.of(tuple.getT1(), tuple.getT2()));
    }

    private RetryBackoffSpec retryPolicy(){
        return Retry.backoff(2, Duration.ofMillis(200)).filter(
                error -> error instanceof WebClientRequestException
                        || error instanceof TimeoutException)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

}
