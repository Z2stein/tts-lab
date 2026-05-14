package com.example.ttslab.audiobooks.repository;

import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentOrigin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AudiobookSpeechSegmentRepository extends JpaRepository<AudiobookSpeechSegment, String> {
    @Query("SELECT s FROM AudiobookSpeechSegment s WHERE s.project.id = ?1 ORDER BY s.orderIndex ASC")
    List<AudiobookSpeechSegment> findByProjectIdOrderByOrderIndex(String projectId);

    @Query("SELECT s FROM AudiobookSpeechSegment s WHERE s.project.id = ?1 AND s.segmentOrigin = ?2 ORDER BY s.orderIndex ASC")
    List<AudiobookSpeechSegment> findByProjectIdAndSegmentOriginOrderByOrderIndex(String projectId, AudiobookSpeechSegmentOrigin segmentOrigin);

    @Query("SELECT s FROM AudiobookSpeechSegment s WHERE s.project.id = ?1")
    List<AudiobookSpeechSegment> findByProjectId(String projectId);

    @Query("SELECT s FROM AudiobookSpeechSegment s WHERE s.id = ?1 AND s.project.id = ?2")
    Optional<AudiobookSpeechSegment> findByIdAndProjectId(String id, String projectId);

    @Modifying
    @Query("DELETE FROM AudiobookSpeechSegment s WHERE s.project.id = ?1 AND s.segmentOrigin = ?2")
    void deleteByProjectIdAndSegmentOrigin(String projectId, AudiobookSpeechSegmentOrigin segmentOrigin);

    @Query("SELECT s FROM AudiobookSpeechSegment s WHERE s.project.id = ?1 AND s.orderIndex = ?2")
    AudiobookSpeechSegment findByProjectIdAndOrderIndex(String id, int i);
}
