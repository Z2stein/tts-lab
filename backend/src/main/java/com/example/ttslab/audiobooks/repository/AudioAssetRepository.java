package com.example.ttslab.audiobooks.repository;

import com.example.ttslab.audiobooks.model.AudioAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AudioAssetRepository extends JpaRepository<AudioAsset, String> {
    @Query("SELECT a FROM AudioAsset a WHERE a.project.id = ?1")
    List<AudioAsset> findByProjectId(String projectId);

    @Query("SELECT a FROM AudioAsset a WHERE a.segment.id = ?1")
    List<AudioAsset> findBySegmentId(String segmentId);
}
