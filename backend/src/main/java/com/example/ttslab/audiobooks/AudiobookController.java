package com.example.ttslab.audiobooks;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.prompts.CurrentUserResolver;
import com.example.ttslab.storage.StoredFile;
import java.io.IOException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audiobooks")
public class AudiobookController {
    private final CurrentUserResolver currentUserResolver;
    private final AudiobookLibraryService audiobookLibraryService;

    public AudiobookController(CurrentUserResolver currentUserResolver, AudiobookLibraryService audiobookLibraryService) {
        this.currentUserResolver = currentUserResolver;
        this.audiobookLibraryService = audiobookLibraryService;
    }

    @GetMapping
    public AudiobookSummaryResponse list(Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        return audiobookLibraryService.list(user);
    }

    @GetMapping("/{id}")
    public AudiobookDetailResponse detail(@PathVariable String id, Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        return audiobookLibraryService.detail(user, id);
    }

    @GetMapping("/{id}/audio-assets/{assetId}/download")
    public ResponseEntity<InputStreamResource> download(
        @PathVariable String id,
        @PathVariable String assetId,
        Authentication authentication
    ) throws IOException {
        CurrentUser user = currentUserResolver.resolve(authentication);
        AudioAsset asset = audiobookLibraryService.assetForDownload(user, id, assetId);
        StoredFile storedFile = audiobookLibraryService.read(asset);
        return audioResponse(asset, storedFile, ContentDisposition.attachment().filename(asset.filename()).build());
    }

    @GetMapping("/{id}/audio-assets/{assetId}/stream")
    public ResponseEntity<InputStreamResource> stream(
        @PathVariable String id,
        @PathVariable String assetId,
        Authentication authentication
    ) throws IOException {
        CurrentUser user = currentUserResolver.resolve(authentication);
        AudioAsset asset = audiobookLibraryService.assetForDownload(user, id, assetId);
        StoredFile storedFile = audiobookLibraryService.read(asset);
        return audioResponse(asset, storedFile, ContentDisposition.inline().filename(asset.filename()).build());
    }

    private ResponseEntity<InputStreamResource> audioResponse(
        AudioAsset asset,
        StoredFile storedFile,
        ContentDisposition contentDisposition
    ) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .contentType(MediaType.parseMediaType(asset.contentType()))
            .contentLength(storedFile.sizeBytes())
            .body(new InputStreamResource(storedFile.content()));
    }
}
