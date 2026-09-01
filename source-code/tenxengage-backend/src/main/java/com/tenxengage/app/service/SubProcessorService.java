package com.tenxengage.app.service;

import com.tenxengage.app.entity.SubProcessor;
import com.tenxengage.app.entity.enums.DpaStatus;
import com.tenxengage.app.entity.enums.SccStatus;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.SubProcessorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SubProcessorService {

    private static final Logger log = LoggerFactory.getLogger(SubProcessorService.class);

    private final SubProcessorRepository subProcessorRepository;

    public SubProcessorService(SubProcessorRepository subProcessorRepository) {
        this.subProcessorRepository = subProcessorRepository;
    }

    @Transactional(readOnly = true)
    public List<SubProcessor> getAll() {
        return subProcessorRepository.findAll();
    }

    @Transactional
    public SubProcessor create(String name, String purpose, String dataProcessed,
                               String location, String dpaStatus, String sccStatus) {
        SubProcessor processor = SubProcessor.builder()
                .name(name)
                .purpose(purpose)
                .dataProcessed(dataProcessed)
                .location(location)
                .dpaStatus(DpaStatus.valueOf(dpaStatus))
                .sccStatus(SccStatus.valueOf(sccStatus))
                .addedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        SubProcessor saved = subProcessorRepository.save(processor);
        log.info("Sub-processor created: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public SubProcessor update(UUID id, String name, String purpose, String dataProcessed,
                               String location, String dpaStatus, String sccStatus) {
        SubProcessor processor = subProcessorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubProcessor", "id", id));

        processor.setName(name);
        processor.setPurpose(purpose);
        processor.setDataProcessed(dataProcessed);
        processor.setLocation(location);
        processor.setDpaStatus(DpaStatus.valueOf(dpaStatus));
        processor.setSccStatus(SccStatus.valueOf(sccStatus));
        processor.setUpdatedAt(Instant.now());

        SubProcessor saved = subProcessorRepository.save(processor);
        log.info("Sub-processor updated: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        SubProcessor processor = subProcessorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubProcessor", "id", id));
        subProcessorRepository.delete(processor);
        log.info("Sub-processor deleted: id={}, name={}", id, processor.getName());
    }
}
