package com.tenxengage.app.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CookieUtilTest {

    private CookieUtil cookieUtil;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        cookieUtil = new CookieUtil(true); // secure=true
        response = new MockHttpServletResponse();
    }

    // -------------------------------------------------------------------------
    // Access Token Cookie
    // -------------------------------------------------------------------------

    @Test
    void addAccessTokenCookie_setsHttpOnly() {
        cookieUtil.addAccessTokenCookie(response, "token", 3600000L);

        Cookie cookie = response.getCookie(CookieUtil.ACCESS_TOKEN_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    void addAccessTokenCookie_setsSecureFlag() {
        cookieUtil.addAccessTokenCookie(response, "token", 3600000L);

        Cookie cookie = response.getCookie(CookieUtil.ACCESS_TOKEN_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getSecure()).isTrue();
    }

    @Test
    void addAccessTokenCookie_setsApiPath() {
        cookieUtil.addAccessTokenCookie(response, "token", 3600000L);

        Cookie cookie = response.getCookie(CookieUtil.ACCESS_TOKEN_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getPath()).isEqualTo("/api/");
    }

    @Test
    void addAccessTokenCookie_setsMaxAgeFromMs() {
        cookieUtil.addAccessTokenCookie(response, "token", 3600000L);

        Cookie cookie = response.getCookie(CookieUtil.ACCESS_TOKEN_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getMaxAge()).isEqualTo(3600);
    }

    @Test
    void addAccessTokenCookie_setsSameSiteStrict() {
        cookieUtil.addAccessTokenCookie(response, "token", 3600000L);

        Cookie cookie = response.getCookie(CookieUtil.ACCESS_TOKEN_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
    }

    // -------------------------------------------------------------------------
    // Refresh Token Cookie
    // -------------------------------------------------------------------------

    @Test
    void addRefreshTokenCookie_setsNarrowAuthPath() {
        cookieUtil.addRefreshTokenCookie(response, "refresh", 604800000L);

        Cookie cookie = response.getCookie(CookieUtil.REFRESH_TOKEN_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth/");
    }

    @Test
    void addRefreshTokenCookie_setsHttpOnly() {
        cookieUtil.addRefreshTokenCookie(response, "refresh", 604800000L);

        Cookie cookie = response.getCookie(CookieUtil.REFRESH_TOKEN_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    void addRefreshTokenCookie_setsSameSiteStrict() {
        cookieUtil.addRefreshTokenCookie(response, "refresh", 604800000L);

        Cookie cookie = response.getCookie(CookieUtil.REFRESH_TOKEN_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
    }

    // -------------------------------------------------------------------------
    // Clear Cookies
    // -------------------------------------------------------------------------

    @Test
    void clearAuthCookies_setsMaxAgeZero() {
        cookieUtil.clearAuthCookies(response);

        Cookie accessCookie = response.getCookie(CookieUtil.ACCESS_TOKEN_COOKIE);
        Cookie refreshCookie = response.getCookie(CookieUtil.REFRESH_TOKEN_COOKIE);

        assertThat(accessCookie).isNotNull();
        assertThat(accessCookie.getMaxAge()).isEqualTo(0);
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getMaxAge()).isEqualTo(0);
    }

    @Test
    void clearAuthCookies_setsEmptyValue() {
        cookieUtil.clearAuthCookies(response);

        Cookie accessCookie = response.getCookie(CookieUtil.ACCESS_TOKEN_COOKIE);
        assertThat(accessCookie).isNotNull();
        assertThat(accessCookie.getValue()).isEmpty();
    }

    @Test
    void clearAuthCookies_preservesSecurityAttributes() {
        cookieUtil.clearAuthCookies(response);

        Cookie accessCookie = response.getCookie(CookieUtil.ACCESS_TOKEN_COOKIE);
        assertThat(accessCookie).isNotNull();
        assertThat(accessCookie.isHttpOnly()).isTrue();
        assertThat(accessCookie.getSecure()).isTrue();
        assertThat(accessCookie.getAttribute("SameSite")).isEqualTo("Strict");
    }

    // -------------------------------------------------------------------------
    // Secure flag configuration
    // -------------------------------------------------------------------------

    @Test
    void secureFalse_doesNotSetSecureFlag() {
        CookieUtil insecureUtil = new CookieUtil(false);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        insecureUtil.addAccessTokenCookie(resp, "token", 3600000L);

        Cookie cookie = resp.getCookie(CookieUtil.ACCESS_TOKEN_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getSecure()).isFalse();
    }
}
