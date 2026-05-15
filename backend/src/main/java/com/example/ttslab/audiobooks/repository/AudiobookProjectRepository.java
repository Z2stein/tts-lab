package com.example.ttslab.audiobooks.repository;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AudiobookProjectRepository extends JpaRepository<AudiobookProject, String> {
    List<AudiobookProject> findByUserId(String userId);
    List<AudiobookProject> findByUserIdOrderByUpdatedAtDescCreatedAtDesc(String userId);
    Optional<AudiobookProject> findByIdAndUserId(String id, String userId);

    @EntityGraph(attributePaths = {
        "speechSegments",
        "speechSegments.character"
    })
    @Query("SELECT p FROM AudiobookProject p WHERE p.id = :id")
    Optional<AudiobookProject> findWithDetailsById(@Param("id") String id);
}
