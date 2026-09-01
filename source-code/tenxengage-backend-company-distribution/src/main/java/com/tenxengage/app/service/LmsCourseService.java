package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.LmsCourseResponse;
import com.tenxengage.app.repository.LmsCourseRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LmsCourseService {

    private final LmsCourseRepository lmsCourseRepository;

    public LmsCourseService(LmsCourseRepository lmsCourseRepository) {
        this.lmsCourseRepository = lmsCourseRepository;
    }

    @Transactional(readOnly = true)
    public List<LmsCourseResponse> getCourses(String category, String search) {
        if (category != null && search != null) {
            return lmsCourseRepository.searchByCategory(category, search).stream()
                    .map(LmsCourseResponse::from)
                    .toList();
        }
        if (search != null) {
            return lmsCourseRepository.search(search).stream()
                    .map(LmsCourseResponse::from)
                    .toList();
        }
        if (category != null) {
            return lmsCourseRepository.findByCategoryOrderByName(category).stream()
                    .map(LmsCourseResponse::from)
                    .toList();
        }
        return lmsCourseRepository.findAll().stream()
                .map(LmsCourseResponse::from)
                .toList();
    }

    @Cacheable(value = "lmsCourseCategories")
    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return lmsCourseRepository.findDistinctCategories();
    }
}
