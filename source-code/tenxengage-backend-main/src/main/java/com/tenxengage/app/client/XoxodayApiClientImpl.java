package com.tenxengage.app.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Service
@Profile("!(local | localtest | test)")
public class XoxodayApiClientImpl implements XoxodayApiClient {

    private static final Logger log = LoggerFactory.getLogger(XoxodayApiClientImpl.class);

    private final String baseUrl;
    private final RestClient restClient;

    public XoxodayApiClientImpl(@Value("${xoxoday.api.base-url:}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl.isBlank() ? "http://localhost" : baseUrl)
                .build();
    }

    @Override
    public List<XoxodayProductResponse> fetchAllProducts() {
        if (baseUrl.isBlank()) {
            log.warn("xoxoday.api.base-url not configured — skipping product fetch, returning empty catalog");
            return List.of();
        }
        try {
            XoxodayProductResponse[] products = restClient.get()
                    .uri("/api/v1/products")
                    .retrieve()
                    .body(XoxodayProductResponse[].class);
            return products != null ? List.of(products) : List.of();
        } catch (RestClientException e) {
            log.error("Failed to fetch Xoxoday products: {}", e.getMessage(), e);
            throw e;
        }
    }
}
