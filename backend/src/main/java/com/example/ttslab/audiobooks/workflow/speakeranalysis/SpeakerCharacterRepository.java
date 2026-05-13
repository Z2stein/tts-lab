package com.example.ttslab.audiobooks.workflow.speakeranalysis;

import com.example.ttslab.audiobooks.model.SpeakerCharacter;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpeakerCharacterRepository extends JpaRepository<SpeakerCharacter, String> {
    List<SpeakerCharacter> findByProjectIdOrderBySortOrderAsc(String projectId);

    void deleteByProjectId(String projectId);
}

