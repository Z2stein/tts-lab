package com.example.ttslab.audiobooks;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.error.ApiException;
import com.example.ttslab.storage.FileStorageService;
import com.example.ttslab.storage.StorageKeyBuilder;
import com.example.ttslab.storage.StorageProperties;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AudiobookLibraryServiceTest {
    @Test
    void rejectsDownloadWhenAssetDoesNotBelongToCurrentUser() {
        AudiobookRepository repository = Mockito.mock(AudiobookRepository.class);
        FileStorageService storage = Mockito.mock(FileStorageService.class);
        AudiobookLibraryService service = new AudiobookLibraryService(
            repository,
            storage,
            new StorageKeyBuilder(new StorageProperties("./data", "app", "feature", "branch"))
        );
        CurrentUser user = new CurrentUser("user-1", "user@example.com", "User", List.of("USER"), "mock");

        when(repository.findAssetForUser("project-1", "asset-1", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assetForDownload(user, "project-1", "asset-1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("The requested audio asset was not found.");
    }

    @Test
    void rejectsDownloadWhenAssetIsNotReady() {
        AudiobookRepository repository = Mockito.mock(AudiobookRepository.class);
        FileStorageService storage = Mockito.mock(FileStorageService.class);
        AudiobookLibraryService service = new AudiobookLibraryService(
            repository,
            storage,
            new StorageKeyBuilder(new StorageProperties("./data", "app", "feature", "branch"))
        );
        CurrentUser user = new CurrentUser("user-1", "user@example.com", "User", List.of("USER"), "mock");
        AudioAsset asset = new AudioAsset("asset-1", "project-1", null, AudioAssetType.PREVIEW_MP3, 1, "key", "a.mp3", "audio/mpeg", 3, null, AudioAssetStatus.GENERATING, Instant.now());

        when(repository.findAssetForUser("project-1", "asset-1", "user-1")).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> service.assetForDownload(user, "project-1", "asset-1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("The requested audio asset is not ready yet.");
    }
}
