package com.example.ttslab.audiobooks;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.prompts.CurrentUserResolver;
import com.example.ttslab.storage.StoredFile;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audiobooks")
public class AudiobookController {
    private final CurrentUserResolver currentUserResolver;
    private final AudiobookService audiobookService;
    private final AudiobookLibraryService audiobookLibraryService;

    public AudiobookController(
        CurrentUserResolver currentUserResolver,
        AudiobookService audiobookService,
        AudiobookLibraryService audiobookLibraryService
    ) {
        this.currentUserResolver = currentUserResolver;
        this.audiobookService = audiobookService;
        this.audiobookLibraryService = audiobookLibraryService;
    }

    // ==================== Project CRUD ====================

    @GetMapping
    public List<AudiobookProjectResponse> list(Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        return audiobookService.listProjectsForUser(user.id()).stream()
            .map(this::toProjectResponse)
            .collect(Collectors.toList());
    }

    @PostMapping
    public AudiobookProjectResponse create(@RequestBody CreateProjectRequest request, Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        AudiobookProject project = audiobookService.createAudiobookProject(
            user.id(),
            request.title(),
            request.sourceText(),
            request.languageCode(),
            request.modelName(),
            request.audioEncoding()
        );
        return toProjectResponse(project);
    }

    @GetMapping("/{projectId}")
    public AudiobookProjectResponse get(@PathVariable String projectId, Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        AudiobookProject project = audiobookService.getProjectForUser(projectId, user.id());
        return toProjectResponse(project);
    }

    @PutMapping("/{projectId}")
    public AudiobookProjectResponse update(
        @PathVariable String projectId,
        @RequestBody UpdateProjectRequest request,
        Authentication authentication
    ) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        AudiobookProject project = audiobookService.getProjectForUser(projectId, user.id());

        if (request.status() != null) {
            audiobookService.updateProjectStatus(projectId, request.status());
        }

        AudiobookProject updated = audiobookService.getProjectForUser(projectId, user.id());
        return toProjectResponse(updated);
    }

    // ==================== Character Management ====================

    @GetMapping("/{projectId}/characters")
    public List<CharacterResponse> listCharacters(@PathVariable String projectId, Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        audiobookService.getProjectForUser(projectId, user.id()); // Verify access
        return audiobookService.getCharactersForProject(projectId).stream()
            .map(this::toCharacterResponse)
            .collect(Collectors.toList());
    }

    @PostMapping("/{projectId}/characters")
    public CharacterResponse addCharacter(
        @PathVariable String projectId,
        @RequestBody CreateCharacterRequest request,
        Authentication authentication
    ) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        audiobookService.getProjectForUser(projectId, user.id()); // Verify access

        Character character = audiobookService.castCharacter(
            projectId,
            request.name(),
            request.roleDescription(),
            request.voiceKey(),
            request.sortOrder()
        );
        return toCharacterResponse(character);
    }

    @GetMapping("/{projectId}/characters/{characterId}")
    public CharacterResponse getCharacter(
        @PathVariable String projectId,
        @PathVariable String characterId,
        Authentication authentication
    ) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        audiobookService.getProjectForUser(projectId, user.id()); // Verify access
        Character character = audiobookService.getCharacter(characterId, projectId);
        return toCharacterResponse(character);
    }

    @PutMapping("/{projectId}/characters/{characterId}")
    public CharacterResponse updateCharacter(
        @PathVariable String projectId,
        @PathVariable String characterId,
        @RequestBody UpdateCharacterRequest request,
        Authentication authentication
    ) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        audiobookService.getProjectForUser(projectId, user.id()); // Verify access

        Character character = audiobookService.getCharacter(characterId, projectId);
        Character updated = new Character(
            character.id(),
            character.projectId(),
            request.name() != null ? request.name() : character.name(),
            request.roleDescription() != null ? request.roleDescription() : character.roleDescription(),
            request.voiceKey() != null ? request.voiceKey() : character.voiceKey(),
            request.sortOrder() != null ? request.sortOrder() : character.sortOrder(),
            request.approved() != null ? request.approved() : character.approved(),
            character.createdAt(),
            java.time.Instant.now()
        );
        audiobookService.updateCharacter(updated);
        return toCharacterResponse(updated);
    }

    @DeleteMapping("/{projectId}/characters/{characterId}")
    public void deleteCharacter(
        @PathVariable String projectId,
        @PathVariable String characterId,
        Authentication authentication
    ) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        audiobookService.getProjectForUser(projectId, user.id()); // Verify access
        audiobookService.removeCharacter(characterId);
    }

    // ==================== Segment Management ====================

    @GetMapping("/{projectId}/segments")
    public List<SpeechSegmentResponse> listSegments(@PathVariable String projectId, Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        audiobookService.getProjectForUser(projectId, user.id()); // Verify access

        List<SpeechSegment> segments = audiobookService.getSegmentsForProject(projectId);
        List<Character> characters = audiobookService.getCharactersForProject(projectId);
        var characterMap = characters.stream().collect(Collectors.toMap(Character::id, c -> c));

        return segments.stream()
            .map(seg -> toSegmentResponse(seg, characterMap.get(seg.characterId())))
            .collect(Collectors.toList());
    }

    @PostMapping("/{projectId}/segments")
    public SpeechSegmentResponse addSegment(
        @PathVariable String projectId,
        @RequestBody CreateSegmentRequest request,
        Authentication authentication
    ) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        audiobookService.getProjectForUser(projectId, user.id()); // Verify access
        audiobookService.getCharacter(request.characterId(), projectId); // Verify character exists

        SpeechSegment segment = audiobookService.addSegment(
            projectId,
            request.characterId(),
            request.sequenceNo(),
            request.originalText()
        );
        Character character = audiobookService.getCharacter(segment.characterId(), projectId);
        return toSegmentResponse(segment, character);
    }

    @GetMapping("/{projectId}/segments/{segmentId}")
    public SpeechSegmentResponse getSegment(
        @PathVariable String projectId,
        @PathVariable String segmentId,
        Authentication authentication
    ) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        audiobookService.getProjectForUser(projectId, user.id()); // Verify access

        SpeechSegment segment = audiobookService.getSegment(segmentId, projectId);
        Character character = audiobookService.getCharacter(segment.characterId(), projectId);
        return toSegmentResponse(segment, character);
    }

    @PutMapping("/{projectId}/segments/{segmentId}")
    public SpeechSegmentResponse updateSegment(
        @PathVariable String projectId,
        @PathVariable String segmentId,
        @RequestBody UpdateSegmentRequest request,
        Authentication authentication
    ) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        audiobookService.getProjectForUser(projectId, user.id()); // Verify access

        SpeechSegment segment = audiobookService.getSegment(segmentId, projectId);
        SpeechSegment updated = new SpeechSegment(
            segment.id(),
            segment.projectId(),
            segment.characterId(),
            segment.sequenceNo(),
            request.originalText() != null ? request.originalText() : segment.originalText(),
            request.annotatedText() != null ? request.annotatedText() : segment.annotatedText(),
            request.edited() != null ? request.edited() : segment.edited(),
            request.approved() != null ? request.approved() : segment.approved(),
            segment.createdAt(),
            java.time.Instant.now()
        );
        audiobookService.updateSegment(updated);
        Character character = audiobookService.getCharacter(segment.characterId(), projectId);
        return toSegmentResponse(updated, character);
    }

    @DeleteMapping("/{projectId}/segments/{segmentId}")
    public void deleteSegment(
        @PathVariable String projectId,
        @PathVariable String segmentId,
        Authentication authentication
    ) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        audiobookService.getProjectForUser(projectId, user.id()); // Verify access
        audiobookService.removeSegment(segmentId);
    }

    // ==================== Generation & Library Display ====================

    @PostMapping("/{projectId}/generate")
    public GenerationRunResponse startGeneration(
        @PathVariable String projectId,
        @RequestBody StartGenerationRequest request,
        Authentication authentication
    ) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        audiobookService.getProjectForUser(projectId, user.id()); // Verify access

        AIGenerationRun run = audiobookService.startGenerationRun(projectId, request.type(), request.requestJson());
        return toGenerationRunResponse(run, List.of());
    }

    @GetMapping("/{projectId}/generation-runs")
    public List<GenerationRunResponse> listGenerationRuns(
        @PathVariable String projectId,
        Authentication authentication
    ) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        audiobookService.getProjectForUser(projectId, user.id()); // Verify access

        return audiobookService.getGenerationRunsForProject(projectId).stream()
            .map(run -> {
                List<RenderSegment> renders = audiobookService.getRenderSegmentsForRun(run.id());
                return toGenerationRunResponse(run, renders);
            })
            .collect(Collectors.toList());
    }

    @GetMapping("/{projectId}/generation-runs/{runId}")
    public GenerationRunResponse getGenerationRun(
        @PathVariable String projectId,
        @PathVariable String runId,
        Authentication authentication
    ) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        audiobookService.getProjectForUser(projectId, user.id()); // Verify access

        AIGenerationRun run = audiobookService.getGenerationRun(runId, projectId);
        List<RenderSegment> renders = audiobookService.getRenderSegmentsForRun(runId);
        return toGenerationRunResponse(run, renders);
    }

    @GetMapping("/{projectId}/library")
    public AudiobookLibraryResponse getLibraryDisplay(
        @PathVariable String projectId,
        Authentication authentication
    ) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        AudiobookProject project = audiobookService.getProjectForUser(projectId, user.id());

        List<Character> characters = audiobookService.getCharactersForProject(projectId);
        List<SpeechSegment> segments = audiobookService.getSegmentsForProject(projectId);
        List<AIGenerationRun> generationRuns = audiobookService.getGenerationRunsForProject(projectId);
        List<AudioAsset> assets = audiobookService.getAssetsForProject(projectId);

        return new AudiobookLibraryResponse(
            toProjectResponse(project),
            characters.stream().map(this::toCharacterResponse).collect(Collectors.toList()),
            segments.stream()
                .map(seg -> toSegmentResponse(seg, characters.stream().filter(c -> c.id().equals(seg.characterId())).findFirst().orElse(null)))
                .collect(Collectors.toList()),
            generationRuns.stream()
                .map(run -> {
                    List<RenderSegment> renders = audiobookService.getRenderSegmentsForRun(run.id());
                    return toGenerationRunResponse(run, renders);
                })
                .collect(Collectors.toList()),
            assets.stream().map(this::toAssetResponse).collect(Collectors.toList())
        );
    }

    // ==================== Asset Download ====================

    @GetMapping("/{projectId}/audio-assets/{assetId}/download")
    public ResponseEntity<InputStreamResource> download(
        @PathVariable String projectId,
        @PathVariable String assetId,
        Authentication authentication
    ) throws IOException {
        CurrentUser user = currentUserResolver.resolve(authentication);
        AudioAsset asset = audiobookService.getAssetForUser(projectId, assetId, user.id());
        StoredFile storedFile = audiobookLibraryService.read(asset);

        ContentDisposition contentDisposition = ContentDisposition.attachment()
            .filename(asset.fileName())
            .build();

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .contentType(MediaType.parseMediaType(asset.contentType()))
            .contentLength(storedFile.sizeBytes())
            .body(new InputStreamResource(storedFile.content()));
    }

    @GetMapping("/{projectId}/audio-assets/{assetId}/stream")
    public ResponseEntity<InputStreamResource> stream(
        @PathVariable String projectId,
        @PathVariable String assetId,
        Authentication authentication
    ) throws IOException {
        CurrentUser user = currentUserResolver.resolve(authentication);
        AudioAsset asset = audiobookService.getAssetForUser(projectId, assetId, user.id());
        StoredFile storedFile = audiobookLibraryService.read(asset);

        ContentDisposition contentDisposition = ContentDisposition.inline()
            .filename(asset.fileName())
            .build();

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .contentType(MediaType.parseMediaType(asset.contentType()))
            .contentLength(storedFile.sizeBytes())
            .body(new InputStreamResource(storedFile.content()));
    }

    // ==================== Response Mappers ====================

    private AudiobookProjectResponse toProjectResponse(AudiobookProject project) {
        return new AudiobookProjectResponse(
            project.id(),
            project.title(),
            project.sourceText(),
            project.languageCode(),
            project.modelName(),
            project.audioEncoding(),
            project.status(),
            project.revision(),
            project.createdAt(),
            project.updatedAt()
        );
    }

    private CharacterResponse toCharacterResponse(Character character) {
        return new CharacterResponse(
            character.id(),
            character.projectId(),
            character.name(),
            character.roleDescription(),
            character.voiceKey(),
            character.sortOrder(),
            character.approved(),
            character.createdAt(),
            character.updatedAt()
        );
    }

    private SpeechSegmentResponse toSegmentResponse(SpeechSegment segment, Character character) {
        return new SpeechSegmentResponse(
            segment.id(),
            segment.projectId(),
            segment.characterId(),
            character != null ? character.name() : null,
            segment.sequenceNo(),
            segment.originalText(),
            segment.annotatedText(),
            segment.edited(),
            segment.approved(),
            segment.createdAt(),
            segment.updatedAt()
        );
    }

    private GenerationRunResponse toGenerationRunResponse(AIGenerationRun run, List<RenderSegment> renders) {
        return new GenerationRunResponse(
            run.id(),
            run.projectId(),
            run.type(),
            run.status(),
            run.startedAt(),
            run.completedAt(),
            renders.stream()
                .map(r -> new GenerationRunResponse.RenderInfo(
                    r.id(),
                    r.speechSegmentId(),
                    r.status(),
                    r.audioAssetId()
                ))
                .collect(Collectors.toList())
        );
    }

    private AudioAssetResponse toAssetResponse(AudioAsset asset) {
        return new AudioAssetResponse(
            asset.id(),
            asset.projectId(),
            asset.type(),
            asset.fileName(),
            asset.durationMs(),
            asset.sizeBytes(),
            asset.createdAt()
        );
    }

    // ==================== Request/Response DTOs ====================

    record CreateProjectRequest(String title, String sourceText, String languageCode, String modelName, String audioEncoding) {}

    record UpdateProjectRequest(AudiobookProjectStatus status) {}

    record CreateCharacterRequest(String name, String roleDescription, String voiceKey, int sortOrder) {}

    record UpdateCharacterRequest(String name, String roleDescription, String voiceKey, Integer sortOrder, Boolean approved) {}

    record CreateSegmentRequest(String characterId, int sequenceNo, String originalText) {}

    record UpdateSegmentRequest(String originalText, String annotatedText, Boolean edited, Boolean approved) {}

    record StartGenerationRequest(RunType type, String requestJson) {}

    record AudiobookProjectResponse(
        String id, String title, String sourceText, String languageCode, String modelName,
        String audioEncoding, AudiobookProjectStatus status, int revision,
        java.time.Instant createdAt, java.time.Instant updatedAt
    ) {}

    record CharacterResponse(
        String id, String projectId, String name, String roleDescription, String voiceKey,
        int sortOrder, boolean approved, java.time.Instant createdAt, java.time.Instant updatedAt
    ) {}

    record SpeechSegmentResponse(
        String id, String projectId, String characterId, String characterName, int sequenceNo,
        String originalText, String annotatedText, boolean edited, boolean approved,
        java.time.Instant createdAt, java.time.Instant updatedAt
    ) {}

    record GenerationRunResponse(
        String id, String projectId, RunType type, RunStatus status,
        java.time.Instant startedAt, java.time.Instant completedAt, List<RenderInfo> renders
    ) {
        record RenderInfo(String id, String speechSegmentId, RunStatus status, String audioAssetId) {}
    }

    record AudioAssetResponse(
        String id, String projectId, AudioAssetType type, String fileName,
        Long durationMs, long sizeBytes, java.time.Instant createdAt
    ) {}

    record AudiobookLibraryResponse(
        AudiobookProjectResponse project, List<CharacterResponse> characters,
        List<SpeechSegmentResponse> segments, List<GenerationRunResponse> generationRuns,
        List<AudioAssetResponse> assets
    ) {}
}
