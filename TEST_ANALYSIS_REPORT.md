# Unit Test Execution and Analysis Report
**Generated:** 2026-05-09 23:40  
**Project:** tts-lab (Angular + Spring Boot)

---

## Executive Summary

| Component | Total Tests | Passed | Failed | Pass Rate | Status |
|-----------|-----------|--------|--------|-----------|--------|
| **Backend (JUnit)** | 98 | 98 | 0 | 100% | ✅ PASS |
| **Frontend (Jasmine)** | 127 | 124 | 3 | 97.6% | ⚠️ FAIL |
| **TOTAL** | **225** | **222** | **3** | **98.7%** | **⚠️ NEEDS ATTENTION** |

---

## Backend Unit Tests: ✅ PASS

**Execution Command:** `gradle clean test`  
**Execution Time:** 21 seconds  
**Test Framework:** JUnit 5 (Spring Boot Test, Mockito, AssertJ)

### Results Summary
- **Total Tests:** 98
- **Passed:** 98 ✅
- **Failed:** 0
- **Ignored:** 0
- **Test Duration:** 5.140 seconds

### Coverage Report
- JaCoCo coverage report generated successfully
- Location: `backend/build/reports/jacoco/test/html/index.html`

### Conclusion
**All backend unit tests passing.** No issues detected. The backend implementation is solid and well-tested.

---

## Frontend Unit Tests: ⚠️ FAIL (3 failures)

**Execution Command:** `npm test` (Karma + Jasmine in ChromeHeadless)  
**Execution Time:** ~45 seconds  
**Test Framework:** Jasmine 6.2.0 + Karma 6.4.4

### Results Summary
- **Total Tests:** 127
- **Passed:** 124 ✅
- **Failed:** 3 ❌
- **Pass Rate:** 97.6%

### Failed Tests Details

#### 1. **Text Length Page Component**
**Test File:** `src/app/features/text-length/text-length-page.component.spec.ts:34`  
**Error Type:** Backend HTTP 500 error  

```
Error: Backend request failed (HTTP 500).
```

**Root Cause:** The test is attempting to make an HTTP request to the backend, but the backend is returning a 500 server error. This could be due to:
- Test mock configuration issue
- Service not properly mocked
- Actual backend service missing or misconfigured

**Impact:** Medium - affects text-length feature tests  
**Recommendation:** 
- Verify the mock HTTP response in the test setup
- Check if the backend endpoint being mocked exists and is properly configured
- Review recent changes to text-length service

---

#### 2. **AudiobookStudioPageComponent - Skips already generated parts**
**Test File:** `src/app/features/audiobook-studio/audiobook-studio-page.component.spec.ts:402`  
**Error Type:** Spy expectation mismatch  

```
Expected spy TtsWorkbenchService.createAudioForRenderRequest to have been called with:
  [ Object({ input: ... }), <jasmine.objectContaining(Object({ signal: ... }))> ]
but actual calls were:
  [ Object({ input: ... }), Object({ signal: ... }), undefined ]

Expected $.length = 3 to equal 2.
Unexpected $[2] = undefined in array.
```

**Root Cause:** The function `createAudioForRenderRequest` is being called with 3 arguments instead of the expected 2. An extra `undefined` argument is being passed.

**Impact:** High - affects audiobook generation feature  
**Recommendation:**
- Check the call site in `audiobook-studio-page.component.ts` around the third argument
- Verify the function signature hasn't changed
- Update test expectations or fix the function call to match the expected signature

---

#### 3. **AudiobookStudioPageComponent - Cancels full generation**
**Test File:** `src/app/features/audiobook-studio/audiobook-studio-page.component.spec.ts:325`  
**Error Type:** Spy expectation mismatch (same pattern as #2)  

```
Expected spy TtsWorkbenchService.createAudioForRenderRequest to have been called with:
  [ Object({ input: ... }), <jasmine.objectContaining(Object({ signal: ... }))> ]
but actual calls were:
  [ Object({ input: ... }), Object({ signal: ... }), undefined ]

Expected $.length = 3 to equal 2.
Unexpected $[2] = undefined in array.
```

**Root Cause:** Same as test #2 - extra `undefined` argument being passed to the spy-monitored function.

**Impact:** High - affects audiobook generation feature  
**Recommendation:** Same as test #2

---

#### 4. **AudiobookStudioPageComponent - Shows player after preview generation**
**Test File:** `src/app/features/audiobook-studio/audiobook-studio-page.component.spec.ts` (line not specified)  
**Error Type:** Spy expectation mismatch (same pattern as #2 and #3)  

```
Expected spy TtsWorkbenchService.createAudioForRenderRequest to have been called with:
  [ Object({ input: Object({  }), voice: ... }), <jasmine.objectContaining(Object({ signal: ... }))> ]
but actual calls were:
  [ Object({ input: Object({  }), voice: ... }), Object({ signal: ... }), undefined ]

Expected $.length = 3 to equal 2.
Unexpected $[2] = undefined in array.
```

**Root Cause:** Same pattern - extra `undefined` argument.

**Impact:** High - affects audiobook preview and player features  
**Recommendation:** Same as tests #2 and #3

---

## Pattern Analysis

### Key Findings

1. **Two Distinct Issues:**
   - **Issue A:** Backend HTTP 500 error in text-length tests (1 failure)
   - **Issue B:** Extra undefined argument to `createAudioForRenderRequest` in audiobook-studio tests (2 failures)

2. **Affected Components:**
   - `TextLengthPageComponent` - Backend integration
   - `AudiobookStudioPageComponent` - Function call signature mismatch

3. **Function Signature Change:**
   The failing tests indicate that `TtsWorkbenchService.createAudioForRenderRequest()` is being called with 3 arguments but the test expects 2. This suggests:
   - The service method signature may have changed recently
   - The component code is passing an extra argument (possibly `undefined`)
   - The test specs haven't been updated to reflect the new signature

---

## Recommendations

### Priority 1: Critical (Blocking)
1. **Fix audiobook-studio-page.component spy expectations** (3 tests)
   - Location: `src/app/features/audiobook-studio/audiobook-studio-page.component.spec.ts`
   - Action: Update spy call expectations to accept 3 arguments instead of 2, OR
   - Action: Remove the extra `undefined` argument from the component code
   - Lines: 325, 402, and unnamed test

### Priority 2: High (Feature Impact)
2. **Fix text-length HTTP 500 error** (1 test)
   - Location: `src/app/features/text-length/text-length-page.component.spec.ts:34`
   - Action: Review mock HTTP setup and backend endpoint configuration
   - Action: Verify the backend service is returning valid responses

---

## Next Steps

1. Review recent commits to `audiobook-studio-page.component.ts` and `tts-workbench.service.ts`
2. Check if the `createAudioForRenderRequest` function signature has changed
3. Update tests or fix component code to align with the actual function signature
4. Investigate the text-length HTTP 500 error and verify mock configuration
5. Re-run tests: `npm test` (frontend) to verify fixes
6. Verify all 127 tests pass before merging changes

---

## Appendix: Test Environment

### Backend Test Environment
- **Test Runner:** Gradle 8.14.3
- **Framework:** Spring Boot Test + JUnit 5
- **Coverage Tool:** JaCoCo
- **Java Version:** OpenJDK 64-Bit Server VM
- **Database:** H2 (in-memory)

### Frontend Test Environment
- **Test Runner:** Karma 6.4.4
- **Framework:** Jasmine 6.2.0
- **Browser:** Chrome Headless 147.0.0.0
- **Node.js Version:** v23.7.0
- **Angular Version:** Based on project configuration

---

**Report End**
