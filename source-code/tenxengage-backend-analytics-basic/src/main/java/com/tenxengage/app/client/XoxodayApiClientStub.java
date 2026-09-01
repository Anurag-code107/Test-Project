package com.tenxengage.app.client;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@Profile({"local", "localtest", "test"})
public class XoxodayApiClientStub implements XoxodayApiClient {

    // Returns 50 sentinel products so runSync()'s two guards both pass:
    //   1. non-empty check: 50 > 0
    //   2. 80% size check: 50 >= (up to ~62 active NON_CASH items in the test DB) * 0.80
    // None of the sentinel IDs exist in the catalog, so integration tests can verify
    // that items absent from the API response get auto-deactivated.
    @Override
    public List<XoxodayProductResponse> fetchAllProducts() {
        return IntStream.rangeClosed(1, 50)
                .mapToObj(i -> new XoxodayProductResponse("STUB-PLACEHOLDER-" + i))
                .collect(Collectors.toList());
    }
}
