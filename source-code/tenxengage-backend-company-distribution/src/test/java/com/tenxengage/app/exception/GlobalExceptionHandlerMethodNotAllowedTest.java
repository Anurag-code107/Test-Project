package com.tenxengage.app.exception;

import com.tenxengage.app.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wrong HTTP method must answer 405, not 500.
 *
 * <p>Regression guard for a real defect: the handler had no mapping for
 * {@link HttpRequestMethodNotSupportedException}, so it fell through to the catch-all and every wrong-method
 * request returned {@code 500 INTERNAL_SERVER_ERROR} — indistinguishable from a crash. Retiring the
 * company-redemption endpoints made it reachable on a path stale clients still call.</p>
 */
class GlobalExceptionHandlerMethodNotAllowedTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static HttpServletRequest requestTo(String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRequestURI(uri);
        return r;
    }

    @Test
    void wrongMethod_returns405_notThe500ItUsedTo() {
        ResponseEntity<ErrorResponse> res = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("POST", List.of("GET")),
                requestTo("/api/v1/redemption/requests/company"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().errorCode()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(res.getBody().errorMessage()).contains("POST");
        assertThat(res.getBody().path()).isEqualTo("/api/v1/redemption/requests/company");
    }

    /** {@code Allow} is what tells a client which method to use instead, so it must be populated. */
    @Test
    void setsAllowHeaderFromTheSupportedMethods() {
        ResponseEntity<ErrorResponse> res = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "PUT")),
                requestTo("/api/v1/redemption/profile"));

        assertThat(res.getHeaders().getAllow()).containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.PUT);
    }

    /**
     * Spring reports no supported methods when the path itself has no mapping at all — which is exactly the
     * retired-endpoint case. Must still answer 405 rather than NPE on the absent header.
     */
    @Test
    void noSupportedMethodsReported_stillAnswers405_withoutAnAllowHeader() {
        ResponseEntity<ErrorResponse> res = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("POST"),
                requestTo("/api/v1/redemption/requests/company"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(res.getHeaders().getAllow()).isEmpty();
    }
}
