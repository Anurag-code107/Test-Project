package com.tenxengage.app.client;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile({"local", "localtest", "test"})
public class XoxodayApiClientStub implements XoxodayApiClient {

    @Override
    public List<XoxodayProductResponse> fetchAllProducts() {
        return List.of();
    }
}
