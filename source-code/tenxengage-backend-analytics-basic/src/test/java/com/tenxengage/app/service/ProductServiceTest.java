package com.tenxengage.app.service;

import com.tenxengage.app.entity.Product;
import com.tenxengage.app.repository.ProductRepository;
import com.tenxengage.app.security.TenantValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private TenantValidator tenantValidator;

    @InjectMocks private ProductService productService;

    private UUID clientId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
    }

    @Test
    void getProducts_returnsAllWhenNoFilters() {
        Product product = Product.builder()
                .clientId(clientId).name("Widget").sku("WDG-001").category("Hardware").build();
        product.setId(UUID.randomUUID());

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(productRepository.findByClientIdOrderByCategoryAscNameAsc(clientId))
                .thenReturn(List.of(product));

        var result = productService.getProducts(null, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void createProduct_throwsOnDuplicateName() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(productRepository.existsByClientIdAndName(clientId, "Existing")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(
                new com.tenxengage.app.dto.request.CreateProductRequest("Existing", "Hardware")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
