# Known Bugs & Technical Debt

**Last Updated:** 2026-05-15  
**Identified During:** AudiobookSpeechSegment refactoring (Senior Developer Review)

---

## Previously Accepted

- Script turn editing is not fully locked while a save is in flight. A second edit can be opened before the first save finishes, and the first save may close the newly opened editor when it completes.

---

## ✅ RESOLVED ISSUES

### 1. Contract Assertion Disabled in CreateAudioIntegrationTest

**File:** `backend/src/test/java/com/example/ttslab/audiobooks/workflow/CreateAudioIntegrationTest.java:722`

**Status:** ✅ RESOLVED (2026-05-15)

**Issue:**
The OpenAPI contract validation was failing for the `/api/audiobooks/workflow/projects/{projectId}/audio-generated` endpoint (POST).

**Root Cause:** Test setup created SpeakerCharacter objects with `null` roleDescription values. The OpenAPI schema requires `SpeakerVoiceAnalysisItem.roleDescription` to be a non-nullable string.

**Fix Applied:**
- Modified `finalizeAudioGenerationMarksTheProjectCurrentAfterAllPartsAreSaved()` test method (line 635-669)
- Changed finalizeFirstCharacter roleDescription from `null` to `"The primary voice guiding the listener through the story"`
- Changed finalizeSecondCharacter roleDescription from `null` to `"A secondary character with distinct personality and voice"`
- Re-enabled the assertion: `assertInteractionMatchesContract(result.getRequest(), result.getResponse());`

**Resolution Details:**
- OpenAPI schema (line 925 in tts-lab-openapi.yaml) marks roleDescription as required for SpeakerVoiceAnalysisItem
- Service layer (AudiobookWorkflowStateService.loadSpeakers(), line 153) correctly maps `character.getRoleDescription()` to response
- Test was failing because it provided null values for required fields
- All 158 backend tests now pass ✅

**Timeline:** RESOLVED - PR ready to merge

---

## 🟠 HIGH-PRIORITY ISSUES

### 2. Hibernate ddl-auto: update Not Production-Safe

**File:** `backend/src/main/resources/application.yml:11`

**Status:** RISKY - Current production configuration

**Issue:**
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update  # ⚠️ Automatic schema mutations
```

**Root Cause:** Development convenience prioritized over production safety.

**Impact:** 🔴 **Data Corruption Risk**
- Concurrent app restarts cause race conditions between Flyway and Hibernate
- No audit trail of schema changes
- Accidental column mutations silently destroy data

**Failure Scenario:**
1. Rolling deployment with 3 instances
2. Instance 1 & 2 start simultaneously, both attempt to add NOT NULL constraint
3. Database locks, timeout occurs
4. Instance 3 crashes, Instance 1 rolls back
5. **Result:** Schema in partial/corrupted state

**Required Fix:**
```yaml
# production-config.yml (environment-specific)
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # ✅ Only validates, never mutates
  flyway:
    enabled: true
    repair-on-migrate: false  # ✅ No auto-repair
```

**Pre-Deployment Validation:**
```bash
# Must pass before any production deployment
psql production -c "SELECT COUNT(*) FROM audiobook_speech_segment WHERE character_id IS NULL;"
# Expected result: 0
```

**Next Steps:**
- [ ] Create `application-prod.yml` with `ddl-auto: validate`
- [ ] Update deployment pipeline to run Flyway BEFORE app startup
- [ ] Test migration on staging with 3+ concurrent app instances
- [ ] Document pre-flight checks in deployment guide
- [ ] Add health check that validates schema is correct

**Timeline:** BLOCKING - Fix before production deployment

---

### 3. No Validation of Character/Project Ownership

**File:** `backend/src/main/java/com/example/ttslab/audiobooks/workflow/service/SpeakerSplitPersistenceService.java`

**Status:** MISSING - No validation implemented

**Issue:**
Characters are assigned to segments without verifying they belong to the same project:

```java
// ⚠️ No validation that character.projectId == segment.projectId
segment.setCharacter(character);
```

**Root Cause:** Missing cross-entity validation at service layer.

**Impact:** 🔴 **Multi-Tenant Isolation Violation**
- Silent creation of invalid relationships (segment from ProjectA with character from ProjectB)
- Data integrity violation not caught until runtime
- Potential privilege escalation (user A's segment using user B's character)

**Required Fix:**
```java
private void validateCharacterBelongsToProject(SpeakerCharacter character, String projectId) {
    if (!character.getProjectId().equals(projectId)) {
        throw new ApiException(
            HttpStatus.BAD_REQUEST,
            "INVALID_CHARACTER_PROJECT",
            "Character must belong to the same project as the segment"
        );
    }
}

// In saveScriptPreviewTurns():
validateCharacterBelongsToProject(character, project.getId());
segment.setCharacter(character);
```

**Test Case Required:**
```java
@Test
void rejectsCharacterFromDifferentProject() {
    SpeakerCharacter charA = createCharacter(projectA, 0, "Narrator", KORE);
    AudiobookSpeechSegment segB = createSegment(projectB, 0, "Speaker");
    
    assertThatThrownBy(() -> {
        segB.setCharacter(charA);
        segmentRepository.save(segB);
    }).isInstanceOf(ApiException.class)
      .hasMessageContaining("same project");
}
```

**Timeline:** Before releasing refactoring (HIGH)

---

## 🟡 MEDIUM-PRIORITY ISSUES

### 4. Test Fixture Duplication - No Factory Pattern

**Files (Examples):**
- `backend/src/test/java/com/example/ttslab/audiobooks/AudiobookLibraryIntegrationTest.java:120-128`
- `backend/src/test/java/com/example/ttslab/audiobooks/workflow/CreateAudioIntegrationTest.java:635-643`
- `backend/src/test/java/com/example/ttslab/audiobooks/workflow/ScriptPreviewWorkflowIntegrationTest.java:94-102`

**Status:** DUPLICATION - Repeated 15+ times across test files

**Issue:**
```java
// Identical pattern repeated in every test:
SpeakerCharacter character = new SpeakerCharacter(
    UUID.randomUUID().toString(),
    projectId,
    sortOrder,
    "Narrator",
    null,
    SpeakerVoice.KORE,
    Instant.parse("2026-05-12T10:00:00Z")
);
speakerCharacterRepository.save(character);
```

**Impact:**
- Harder to maintain (signature change = update 15 places)
- Inconsistent test data across files
- No reusable builders for complex objects

**Recommended Fix:**
```java
// In base test class
protected SpeakerCharacter createCharacter(
    String projectId,
    int sortOrder,
    String name,
    SpeakerVoice voice
) {
    SpeakerCharacter character = new SpeakerCharacter(
        UUID.randomUUID().toString(),
        projectId,
        sortOrder,
        name,
        null,  // roleDescription
        voice,
        Instant.now()
    );
    return speakerCharacterRepository.save(character);
}

// Usage:
SpeakerCharacter narrator = createCharacter(projectA, 0, "Narrator", KORE);
```

**Timeline:** Next sprint (code quality improvement)

---

### 5. Missing Documentation on Lazy-Loading Assumptions

**File:** `backend/src/main/java/com/example/ttslab/audiobooks/workflow/AudiobookWorkflowStateService.java:150`

**Status:** UNDOCUMENTED - No warnings on lazy-load requirements

**Issue:**
```java
private String resolveSpeakerName(AudiobookSpeechSegment segment) {
    SpeakerCharacter character = segment.getCharacter();  // ⚠️ Assumes loaded/in-transaction
    return character.getSpeakerName();
}
```

**Root Cause:** No JavaDoc explaining lazy-loading constraints.

**Impact:**
- Future refactoring may break this without obvious error
- New developers unaware of lazy-loading requirements
- Can cause `LazyInitializationException` in unexpected places

**Required Fix:**
```java
/**
 * Resolves the speaker name from the segment's character.
 * 
 * <strong>IMPORTANT CONSTRAINT:</strong> The segment must have its character relationship
 * eagerly loaded (via @EntityGraph) or be accessed within an active transaction.
 * Calling this with a detached segment outside a transaction will throw LazyInitializationException.
 * 
 * @param segment segment with character pre-loaded or in-transaction
 * @return speaker name from the character
 * @throws IllegalStateException if character is not loaded
 * @see AudiobookSpeechSegmentRepository#findById(String)
 */
private String resolveSpeakerName(AudiobookSpeechSegment segment) {
    SpeakerCharacter character = segment.getCharacter();
    if (character == null) {
        throw new IllegalStateException("Segment character is null or not loaded");
    }
    return character.getSpeakerName();
}
```

**Timeline:** Next code review (documentation)

---

## 🟢 LOW-PRIORITY ISSUES

### 6. Missing Database Indexes on Foreign Keys

**Files:**  None - Performance optimization, not yet implemented

**Status:** OPTIMIZATION - No index on character lookups

**Issue:**
```sql
-- Current: Full table scan for character lookups in projects
SELECT s FROM audiobook_speech_segment s 
WHERE s.project.id = ?  -- No index on (project_id) for karakters
```

**Impact:**
- List operations may do full table scans
- Character lookups in loops are O(n)
- No immediate impact for small datasets

**Recommended Optimization:**
```sql
-- V15__add_character_indexes.sql
CREATE INDEX idx_charakters_project_id ON charakters(project_id);
CREATE INDEX idx_audiobook_speech_segment_character_id ON audiobook_speech_segment(character_id);
```

**Timeline:** Performance optimization phase (not blocking)

---

### 7. No Soft-Delete Strategy for Character Lifecycle

**File:** `backend/src/main/java/com/example/ttslab/audiobooks/model/SpeakerCharacter.java`

**Status:** DESIGN LIMITATION - Hard-delete only

**Issue:**
```java
// Hard-delete cascades to segments:
characterRepository.delete(character);
// Segments become unreachable / orphaned
```

**Impact:**
- If character is deleted, associated segments become unreachable
- No audit trail of deletion
- Limited flexibility for lifecycle management

**Future Enhancement (Post-MVP):**
```java
@Entity
public class SpeakerCharacter {
    // ... fields ...
    
    @Column(nullable = false)
    private boolean isActive = true;
    
    public void deactivate() {
        this.isActive = false;
    }
}

// Queries: findByProjectIdAndIsActive(projectId, true)
```

**Timeline:** Architectural improvement for future phase

---

## Summary Table

| # | Issue | Severity | Component | Status | Owner | Timeline |
|---|-------|----------|-----------|--------|-------|----------|
| 1 | Contract assertion disabled | ✅ RESOLVED | Tests | DONE | Claude | 2026-05-15 ✅ |
| 2 | Hibernate ddl-auto in production | 🟠 HIGH | Config | TODO | DevOps | Before Prod |
| 3 | Missing character/project validation | 🟠 HIGH | Service | TODO | Backend | Before Release |
| 4 | Test fixture duplication | 🟡 MEDIUM | Tests | TODO | Backend | Next Sprint |
| 5 | Missing lazy-load docs | 🟡 MEDIUM | Service | TODO | Backend | Code Review |
| 6 | Missing DB indexes | 🟢 LOW | Performance | TODO | DBA | Optimization |
| 7 | No soft-delete pattern | 🟢 LOW | Design | TODO | Architecture | Future Phase |

---

## How to Update

When resolving an issue:
1. Add timestamp and PR link
2. Move to "RESOLVED" section
3. Keep historical record

When adding new issues:
1. Use appropriate severity emoji
2. Include specific file paths/line numbers
3. Provide reproduction steps
4. List impact and next steps
