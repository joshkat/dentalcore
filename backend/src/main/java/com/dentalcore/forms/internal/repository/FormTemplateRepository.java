package com.dentalcore.forms.internal.repository;

import com.dentalcore.forms.internal.entity.FormTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FormTemplateRepository extends JpaRepository<FormTemplate, UUID> {

    List<FormTemplate> findAllByOrderByActiveDescNameAsc();
}
