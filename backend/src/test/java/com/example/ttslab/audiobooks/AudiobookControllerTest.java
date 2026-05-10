package com.example.ttslab.audiobooks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.error.ApiException;
import com.example.ttslab.prompts.CurrentUserResolver;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AudiobookController.class)
class AudiobookControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CurrentUserResolver currentUserResolver;

    @MockBean
    private AudiobookService audiobookService;

    @MockBean
    private AudiobookLibraryService audiobookLibraryService;

    private final CurrentUser user = new CurrentUser("user-1", "user1@example.com", "User One", List.of("USER"), "mock");

    @BeforeEach
    void setup() {
        when(currentUserResolver.resolve(any())).thenReturn(user);
    }

    @Test
    void listsCurrentUsersAudiobooks() throws Exception {
        Instant now = Instant.now();
        AudiobookProject project = new AudiobookProject(
            "project-1", "user-1", "The Amber Signal",
            "Story text", "en-US", "claude-opus", "mp3",
            AudiobookProjectStatus.NEEDS_REVIEW, 1, now, now
        );
        when(audiobookService.listProjectsForUser("user-1")).thenReturn(List.of(project));

        mockMvc.perform(get("/api/audiobooks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("project-1"))
            .andExpect(jsonPath("$[0].title").value("The Amber Signal"))
            .andExpect(jsonPath("$[0].status").value("NEEDS_REVIEW"));
    }

    @Test
    void getProjectReturnsProjectDetails() throws Exception {
        Instant now = Instant.now();
        AudiobookProject project = new AudiobookProject(
            "project-1", "user-1", "Test Project",
            "Story text", "en-US", "claude-opus", "mp3",
            AudiobookProjectStatus.DRAFT, 1, now, now
        );
        when(audiobookService.getProjectForUser("project-1", "user-1")).thenReturn(project);

        mockMvc.perform(get("/api/audiobooks/project-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("project-1"))
            .andExpect(jsonPath("$.title").value("Test Project"));
    }

    @Test
    @DisplayName("List returns empty items when user has no audiobooks")
    void listReturnsEmptyItemsWhenNoAudiobooks() throws Exception {
        when(audiobookLibraryService.list(user)).thenReturn(new AudiobookSummaryResponse(List.of()));

        mockMvc.perform(get("/api/audiobooks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("Detail returns full audiobook with scenes and assets")
    void detailReturnsFullAudiobookData() throws Exception {
        List<AudiobookSpeechSegmentResponse> scenes = List.of(
            new AudiobookSpeechSegmentResponse(
                "scene-1",
                1,
                "Opening",
                AudiobookSpeechSegmentReviewStatus.APPROVED,
                30,
                "Narrator",
                "Story voice",
                "Clear voice",
                "[calm] Once upon a time"
            )
        );
        List<AudioAssetResponse> assets = List.of(
            new AudioAssetResponse(
                "asset-1",
                "scene-1",
                AudioAssetType.PREVIEW_MP3,
                1,
                "preview.mp3",
                "audio/mpeg",
                5000L,
                30,
                AudioAssetStatus.READY,
                Instant.parse("2026-05-09T10:00:00Z"),
                "/api/audiobooks/project-1/audio-assets/asset-1/download",
                "/api/audiobooks/project-1/audio-assets/asset-1/stream"
            )
        );

        AudiobookDetailResponse detail = new AudiobookDetailResponse(
            "project-1",
            "The Amber Signal",
            AudiobookProjectStatus.NEEDS_REVIEW,
            1,
            1,
            30,
            Instant.parse("2026-05-09T10:00:00Z"),
            Instant.parse("2026-05-09T10:00:00Z"),
            scenes,
            assets
        );

        when(audiobookLibraryService.detail(user, "project-1")).thenReturn(detail);

        mockMvc.perform(get("/api/audiobooks/project-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("project-1"))
            .andExpect(jsonPath("$.title").value("The Amber Signal"))
            .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
            .andExpect(jsonPath("$.sceneCount").value(1))
            .andExpect(jsonPath("$.speakerCount").value(1))
            .andExpect(jsonPath("$.totalDurationSeconds").value(30))
            .andExpect(jsonPath("$.scenes.length()").value(1))
            .andExpect(jsonPath("$.audioAssets.length()").value(1));
    }

    @Test
    @DisplayName("Detail returns 404 when project not found")
    void detailReturnsNotFoundWhenProjectMissing() throws Exception {
        when(audiobookLibraryService.detail(user, "missing-project"))
            .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "AUDIOBOOK_NOT_FOUND", "The requested audiobook was not found."));

        mockMvc.perform(get("/api/audiobooks/missing-project"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("AUDIOBOOK_NOT_FOUND"));
    }

    @Test
    @DisplayName("Download returns 404 when asset not found")
    void downloadReturnsNotFoundWhenAssetMissing() throws Exception {
        when(audiobookLibraryService.assetForDownload(user, "project-1", "missing-asset"))
            .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "AUDIO_ASSET_NOT_FOUND", "The requested audio asset was not found."));

        mockMvc.perform(get("/api/audiobooks/project-1/audio-assets/missing-asset/download"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("AUDIO_ASSET_NOT_FOUND"));
    }

    @Test
    @DisplayName("Download returns 409 when asset not ready")
    void downloadReturnsConflictWhenAssetNotReady() throws Exception {
        when(audiobookLibraryService.assetForDownload(user, "project-1", "asset-1"))
            .thenThrow(new ApiException(HttpStatus.CONFLICT, "AUDIO_ASSET_NOT_READY", "The requested audio asset is not ready yet."));

        mockMvc.perform(get("/api/audiobooks/project-1/audio-assets/asset-1/download"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("AUDIO_ASSET_NOT_READY"));
    }

    @Test
    @DisplayName("Download has correct content type for MP3")
    void downloadHasCorrectContentTypeForMp3() throws Exception {
        AudioAsset asset = new AudioAsset(
            "asset-mp3",
            "project-1",
            null,
            AudioAssetType.PREVIEW_MP3,
            1,
            "storage/audio.mp3",
            "audio.mp3",
            "audio/mpeg",
            1000L,
            60,
            AudioAssetStatus.READY,
            Instant.now()
        );
        when(audiobookLibraryService.assetForDownload(user, "project-1", "asset-mp3")).thenReturn(asset);
        when(audiobookLibraryService.read(asset)).thenReturn(new StoredFile(new ByteArrayInputStream(new byte[] {1, 2, 3}), "audio/mpeg", 1000L));

        mockMvc.perform(get("/api/audiobooks/project-1/audio-assets/asset-mp3/download"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "audio/mpeg"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"audio.mp3\""));
    }
}
