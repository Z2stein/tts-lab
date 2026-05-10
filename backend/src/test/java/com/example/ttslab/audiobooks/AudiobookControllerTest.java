package com.example.ttslab.audiobooks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.prompts.CurrentUserResolver;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
}
