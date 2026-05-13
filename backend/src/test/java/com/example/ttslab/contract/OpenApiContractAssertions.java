package com.example.ttslab.contract;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public final class OpenApiContractAssertions {
    private static final OpenApiInteractionValidator VALIDATOR = OpenApiInteractionValidator
        .createForSpecificationUrl(resolveSpecPath().toUri().toString())
        .build();

    private OpenApiContractAssertions() {
    }

    public static void assertInteractionMatchesContract(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        ValidationReport report = VALIDATOR.validate(buildRequest(request), buildResponse(response));
        assertNoErrors(report);
    }

    public static void assertResponseMatchesContract(String path, Request.Method method, MockHttpServletResponse response) throws Exception {
        ValidationReport report = VALIDATOR.validateResponse(path, method, buildResponse(response));
        assertNoErrors(report);
    }

    private static SimpleRequest buildRequest(MockHttpServletRequest request) throws Exception {
        SimpleRequest.Builder builder = new SimpleRequest.Builder(request.getMethod(), request.getRequestURI());
        String contentType = request.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            builder.withHeader("Content-Type", contentType);
        }
        request.getParameterMap().forEach((name, values) -> builder.withQueryParam(name, values));
        for (String headerName : Collections.list(request.getHeaderNames())) {
            builder.withHeader(headerName, headerValues(request, headerName));
        }
        byte[] content = request.getContentAsByteArray();
        if (content != null && content.length > 0) {
            builder.withBody(new String(content, StandardCharsets.UTF_8));
        }
        return builder.build();
    }

    private static SimpleResponse buildResponse(MockHttpServletResponse response) throws Exception {
        SimpleResponse.Builder builder = new SimpleResponse.Builder(response.getStatus());
        String contentType = response.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            builder.withContentType(contentType);
        }
        for (String headerName : response.getHeaderNames()) {
            builder.withHeader(headerName, headerValues(response.getHeaders(headerName)));
        }
        byte[] content = response.getContentAsByteArray();
        if (content != null && content.length > 0) {
            builder.withBody(content);
        }
        return builder.build();
    }

    private static String[] headerValues(MockHttpServletRequest request, String headerName) {
        return Collections.list(request.getHeaders(headerName)).toArray(String[]::new);
    }

    private static String[] headerValues(Collection<String> values) {
        return values.toArray(String[]::new);
    }

    private static Path resolveSpecPath() {
        List<Path> candidates = List.of(
            Paths.get("..", "shared", "api-contract", "tts-lab-openapi.yaml"),
            Paths.get("shared", "api-contract", "tts-lab-openapi.yaml")
        );
        return candidates.stream()
            .map(Path::toAbsolutePath)
            .filter(path -> path.toFile().exists())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unable to locate shared OpenAPI contract file."));
    }

    private static void assertNoErrors(ValidationReport report) {
        if (!report.hasErrors()) {
            return;
        }
        String messages = report.getMessages().stream()
            .map(message -> message.getLevel() + ": " + message.getMessage())
            .collect(Collectors.joining(System.lineSeparator()));
        Assertions.fail("OpenAPI contract validation failed:" + System.lineSeparator() + messages);
    }

}
