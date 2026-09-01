package com.tenxengage.app.service;

import com.tenxengage.app.entity.SubProcessor;
import com.tenxengage.app.entity.enums.DpaStatus;
import com.tenxengage.app.entity.enums.SccStatus;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.SubProcessorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubProcessorServiceTest {

    @Mock
    private SubProcessorRepository subProcessorRepository;

    @InjectMocks
    private SubProcessorService subProcessorService;

    private SubProcessor testProcessor;
    private UUID processorId;

    @BeforeEach
    void setUp() {
        processorId = UUID.randomUUID();

        testProcessor = SubProcessor.builder()
                .id(processorId)
                .name("AWS S3")
                .purpose("File storage")
                .dataProcessed("User uploaded documents")
                .location("US")
                .dpaStatus(DpaStatus.SIGNED)
                .sccStatus(SccStatus.NOT_REQUIRED)
                .addedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void getAll_returnsAllProcessors() {
        SubProcessor secondProcessor = SubProcessor.builder()
                .id(UUID.randomUUID())
                .name("SendGrid")
                .purpose("Email delivery")
                .dataProcessed("User email addresses")
                .location("US")
                .dpaStatus(DpaStatus.SIGNED)
                .sccStatus(SccStatus.NOT_REQUIRED)
                .build();

        when(subProcessorRepository.findAll()).thenReturn(List.of(testProcessor, secondProcessor));

        List<SubProcessor> result = subProcessorService.getAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("AWS S3");
        assertThat(result.get(1).getName()).isEqualTo("SendGrid");
    }

    @Test
    void create_savesNewProcessor() {
        when(subProcessorRepository.save(any(SubProcessor.class))).thenReturn(testProcessor);

        SubProcessor result = subProcessorService.create(
                "AWS S3", "File storage", "User uploaded documents",
                "US", "SIGNED", "NOT_REQUIRED");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("AWS S3");
        assertThat(result.getDpaStatus()).isEqualTo(DpaStatus.SIGNED);
        verify(subProcessorRepository).save(any(SubProcessor.class));
    }

    @Test
    void update_modifiesExistingProcessor() {
        when(subProcessorRepository.findById(processorId)).thenReturn(Optional.of(testProcessor));
        when(subProcessorRepository.save(any(SubProcessor.class))).thenAnswer(inv -> inv.getArgument(0));

        SubProcessor result = subProcessorService.update(
                processorId, "AWS S3 Updated", "File storage and CDN",
                "User documents and assets", "EU", "SIGNED", "SIGNED");

        assertThat(result.getName()).isEqualTo("AWS S3 Updated");
        assertThat(result.getPurpose()).isEqualTo("File storage and CDN");
        assertThat(result.getLocation()).isEqualTo("EU");
        assertThat(result.getSccStatus()).isEqualTo(SccStatus.SIGNED);
        verify(subProcessorRepository).save(any(SubProcessor.class));
    }

    @Test
    void delete_removesProcessor() {
        when(subProcessorRepository.findById(processorId)).thenReturn(Optional.of(testProcessor));

        subProcessorService.delete(processorId);

        verify(subProcessorRepository).delete(testProcessor);
    }
}
