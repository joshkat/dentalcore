package com.dentalcore.clinicalnotes.internal.repository;

import com.dentalcore.clinicalnotes.internal.entity.NoteTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NoteTemplateRepository extends JpaRepository<NoteTemplate, UUID> {

    List<NoteTemplate> findAllByOrderByActiveDescNameAsc();
}
