package com.tenxengage.app.config;

import com.tenxengage.app.controller.AiChatController;
import com.tenxengage.app.controller.ApprovalController;
import com.tenxengage.app.controller.AuthController;
import com.tenxengage.app.dto.response.ApiResponse;
import com.tenxengage.app.dto.response.ErrorResponse;
import com.tenxengage.app.dto.response.PaginatedResponse;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice(basePackages = "com.tenxengage.app.controller")
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Skip wrapping for AuthController — frontend expects unwrapped responses
        if (returnType.getContainingClass() == AuthController.class) {
            return false;
        }
        // Skip wrapping for ApprovalController — returns HTML, not JSON
        if (returnType.getContainingClass() == ApprovalController.class) {
            return false;
        }
        // Skip wrapping for AiChatController — returns SSE stream
        if (returnType.getContainingClass() == AiChatController.class) {
            return false;
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        // Don't wrap null bodies (204 No Content)
        if (body == null) {
            return null;
        }

        // Don't double-wrap
        if (body instanceof ApiResponse) {
            return body;
        }

        // Don't wrap error responses — they have their own format
        if (body instanceof ErrorResponse) {
            return body;
        }

        // Don't wrap binary/stream responses (e.g. file downloads)
        if (body instanceof Resource) {
            return body;
        }

        // Don't wrap raw byte arrays — ByteArrayHttpMessageConverter can't handle ApiResponse<byte[]>
        if (body instanceof byte[]) {
            return body;
        }

        // Convert Spring Page to PaginatedResponse before wrapping
        if (body instanceof Page<?> page) {
            PaginatedResponse<?> paginated = PaginatedResponse.from(page);
            return ApiResponse.success(paginated);
        }

        return ApiResponse.success(body);
    }
}
