package com.example.ttslab.audiobooks.repository;

import com.example.ttslab.audiobooks.model.AudioAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AudioAssetRepository extends JpaRepository<AudioAsset, String> {
    @Query("SELECT a FROM AudioAsset a WHERE a.project.id = ?1 ORDER BY a.createdAt DESC")
    List<AudioAsset> findByProjectId(String projectId);

    @Query("SELECT a FROM AudioAsset a WHERE a.segment.id = ?1")
    List<AudioAsset> findBySegmentId(String segmentId);

    @Query("SELECT a FROM AudioAsset a WHERE a.project.id = ?1 ORDER BY a.createdAt DESC")
    List<AudioAsset> findByProjectIdOrderByCreatedAtDesc(String projectId);

    @Query("SELECT a FROM AudioAsset a JOIN a.project p WHERE a.id = ?1 AND a.project.id = ?2 AND p.userId = ?3")
    java.util.Optional<AudioAsset> findByIdAndProjectIdAndProjectUserId(String assetId, String projectId, String userId);
}
