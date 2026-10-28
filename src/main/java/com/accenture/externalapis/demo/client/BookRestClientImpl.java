package com.accenture.externalapis.demo.client;

import com.accenture.externalapis.demo.config.ExternalServiceProperties;
import com.accenture.externalapis.demo.dto.BookApiResponse;
import com.accenture.externalapis.demo.dto.BookDto;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Component
public class BookRestClientImpl implements BookRestClient{

    private final RestClient restClient;

    public BookRestClientImpl(RestClient.Builder builder, ExternalServiceProperties properties) {

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));

        builder.baseUrl(properties.baseUrl());

        if (properties.token() != null && !properties.token().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + properties.token());
        }

        builder.requestFactory(factory);

        this.restClient = builder.build();
    }
        @Override
        public BookDto getBook(Long id){
            try{
                BookApiResponse response = restClient
                        .get()
                        .uri("/books/{id}", id)
                        .retrieve()
                        .body(BookApiResponse.class);

                if (response == null){
                    throw new ClientException("Empty response from external API");
                }

                return new BookDto(
                        response.title(),
                        response.author(),
                        response.genre(),
                        response.price());
            } catch (HttpClientErrorException e){
                throw new ClientException("Client error: " + e.getStatusCode(), e);

            } catch (HttpServerErrorException e){
                throw new ClientException("Server error: " + e.getStatusCode(), e);

            } catch (ResourceAccessException e){
                throw new ClientException("Connection error: " + e.getMessage(), e);

            } catch (RestClientException e){
                throw new ClientException("Failed to fetch book: " + e.getMessage(), e);
            }

    }

    @Override
    public List<BookDto> getAllBooks() {
        try{
            BookApiResponse[] response = restClient.get().uri("/books").retrieve().body(BookApiResponse[].class);

            if (response == null){
                throw new ClientException("Empty response from external API");
            }

            return Arrays.stream(response).map(book -> new BookDto(
                    book.title(),
                    book.author(),
                    book.genre(),
                    book.price())).toList();
        } catch (HttpClientErrorException e){
            throw new ClientException("Client error: " + e.getStatusCode(), e);

        } catch (HttpServerErrorException e){
            throw new ClientException("Server error: " + e.getStatusCode(), e);

        } catch (ResourceAccessException e){
            throw new ClientException("Connection error: " + e.getMessage(), e);

        } catch (RestClientException e){
            throw new ClientException("Failed to fetch book: " + e.getMessage(), e);
        }
    }
}
