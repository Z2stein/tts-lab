package com.example.ttslab.audiobooks;

import static com.example.ttslab.contract.OpenApiContractAssertions.assertInteractionMatchesContract;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ttslab.audiobooks.controller.AudiobookController;
import com.example.ttslab.audiobooks.dto.AudiobookDetailResponse;
import com.example.ttslab.audiobooks.dto.AudiobookSummaryResponse;
import com.example.ttslab.audiobooks.model.AudioAsset;
import com.example.ttslab.audiobooks.model.AudioAssetStatus;
import com.example.ttslab.audiobooks.model.AudioAssetType;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.service.AudiobookLibraryService;
import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.prompts.CurrentUserResolver;
import com.example.ttslab.storage.StoredFile;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AudiobookController.class)
class AudiobookControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CurrentUserResolver currentUserResolver;

    @MockBean
    private AudiobookLibraryService audiobookLibraryService;

    private final CurrentUser user = new CurrentUser("user-1", "user1@example.com", "User One", List.of("USER"), "mock");

    @BeforeEach
    void setup() {
        when(currentUserResolver.resolve(any())).thenReturn(user);
    }

    @Test
    void listsCurrentUsersAudiobooks() throws Exception {
        when(audiobookLibraryService.list(user)).thenReturn(new AudiobookSummaryResponse(List.of(
            new AudiobookSummaryResponse.AudiobookSummaryItem(
                "project-1",
                "The Amber Signal",
                AudiobookProjectStatus.NEEDS_REVIEW,
                2,
                3,
                185,
                Instant.parse("2026-05-09T10:00:00Z"),
                List.of()
            )
        )));

        MvcResult result = mockMvc.perform(get("/api/audiobooks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value("project-1"))
            .andExpect(jsonPath("$.items[0].status").value("NEEDS_REVIEW"))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void returnsCurrentUsersAudiobookDetail() throws Exception {
        when(audiobookLibraryService.detail(user, "project-1")).thenReturn(new AudiobookDetailResponse(
            "project-1",
            "The Amber Signal",
            AudiobookProjectStatus.NEEDS_REVIEW,
            2,
            3,
            185,
            Instant.parse("2026-05-09T09:30:00Z"),
            Instant.parse("2026-05-09T10:00:00Z"),
            List.of(),
            List.of()
        ));

        MvcResult result = mockMvc.perform(get("/api/audiobooks/project-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("project-1"))
            .andExpect(jsonPath("$.title").value("The Amber Signal"))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void downloadsReadyAssetWithAttachmentDisposition() throws Exception {
        AudioAsset asset = new AudioAsset(
            "asset-1",
            "project-1",
            null,
            AudioAssetType.PREVIEW_MP3,
            1,
            "storage/key.mp3",
            "preview.mp3",
            "audio/mpeg",
            3,
            10,
            AudioAssetStatus.READY,
            Instant.parse("2026-05-09T10:00:00Z")
        );
        when(audiobookLibraryService.assetForDownload(user, "project-1", "asset-1")).thenReturn(asset);
        when(audiobookLibraryService.read(asset)).thenReturn(new StoredFile(new ByteArrayInputStream(new byte[] {'I', 'D', '3'}), "audio/mpeg", 3));

        MvcResult result = mockMvc.perform(get("/api/audiobooks/project-1/audio-assets/asset-1/download"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "audio/mpeg"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"preview.mp3\""))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void streamsReadyAssetInline() throws Exception {
        AudioAsset asset = new AudioAsset(
            "asset-1",
            "project-1",
            null,
            AudioAssetType.PREVIEW_MP3,
            1,
            "storage/key.mp3",
            "preview.mp3",
            "audio/mpeg",
            3,
            10,
            AudioAssetStatus.READY,
            Instant.parse("2026-05-09T10:00:00Z")
        );
        when(audiobookLibraryService.assetForDownload(user, "project-1", "asset-1")).thenReturn(asset);
        when(audiobookLibraryService.read(asset)).thenReturn(new StoredFile(new ByteArrayInputStream(new byte[] {'I', 'D', '3'}), "audio/mpeg", 3));

        MvcResult result = mockMvc.perform(get("/api/audiobooks/project-1/audio-assets/asset-1/stream"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "audio/mpeg"))
            .andExpect(header().string("Content-Disposition", "inline; filename=\"preview.mp3\""))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }
}
