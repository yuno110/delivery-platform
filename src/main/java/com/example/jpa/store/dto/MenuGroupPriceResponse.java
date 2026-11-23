package com.example.jpa.store.dto;

public record MenuGroupPriceResponse(
        Long menuGroupId, String menuGroupName, long menuCount,
        Double avgPrice, Long minPrice, Long maxPrice
) {}
