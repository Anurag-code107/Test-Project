package com.tenxengage.admin.config;

import com.tenxengage.admin.controller.AuthController;
import com.tenxengage.admin.dto.response.ApiResponse;
import com.tenxengage.admin.dto.response.ErrorResponse;
import com.tenxengage.admin.dto.response.PaginatedResponse;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice(basePackages = "com.tenxengage.admin.controller")
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Skip wrapping for AuthController — frontend expects unwrapped responses
        if (returnType.getContainingClass() == AuthController.class) {
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
        if (body == null) {
            return null;
        }

        if (body instanceof ApiResponse) {
            return body;
        }

        if (body instanceof ErrorResponse) {
            return body;
        }

        if (body instanceof Resource) {
            return body;
        }

        if (body instanceof Page<?> page) {
            PaginatedResponse<?> paginated = PaginatedResponse.from(page);
            return ApiResponse.success(paginated);
        }

        return ApiResponse.success(body);
    }
}
