package com.accenture.externalapis.demo.dto;

public record BookApiResponse(
        Long id,
        String title,
        String author,
        String genre,
        double price,
        String isbn,
        int publishedYear) {
}
