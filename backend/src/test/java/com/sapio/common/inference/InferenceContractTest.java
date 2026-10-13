package com.sapio.common.inference;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.sapio.study.ai.StudyAi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumer-side contract test: every DTO the backend sends to or reads from the inference service must agree with
 * the committed contract (contracts/inference-openapi.json, kept in sync with the Python code by its own test).
 * Catches renamed or removed fields, missing required fields, enum values one side does not know, and
 * endpoints that no longer exist, before anything is deployed.
 */
class InferenceContractTest {

    private static final Path CONTRACT = Path.of("..", "contracts", "inference-openapi.json");
    private static JsonNode openApi;

    /** One wire call: the endpoint, what the backend sends and what it reads back. */
    record Call(String method, String path, Class<?> request, Class<?> response) {
    }

    private static final List<Call> CALLS = List.of(
            new Call("get", "/health", null, InferenceCompatibilityCheck.Health.class),
            new Call("post", "/v1/extract", null, type("com.sapio.document.extraction.InferenceExtractionGateway$ExtractResponse")),
            new Call("post", "/v1/transcribe", null,
                    type("com.sapio.document.extraction.InferenceExtractionGateway$TranscriptDto")),
            new Call("post", "/v1/extract/transcript",
                    type("com.sapio.document.extraction.InferenceExtractionGateway$LectureNotesRequest"),
                    type("com.sapio.document.extraction.InferenceExtractionGateway$ExtractResponse")),
            new Call("post", "/v1/index/figures", type("com.sapio.knowledge.ai.InferenceKnowledgeAi$DescribeRequest"),
                    type("com.sapio.knowledge.ai.InferenceKnowledgeAi$DescribeResponse")),
            new Call("post", "/v1/index/summaries", type("com.sapio.knowledge.ai.InferenceKnowledgeAi$SummarizeRequest"),
                    type("com.sapio.knowledge.ai.InferenceKnowledgeAi$SummarizeResponse")),
            new Call("post", "/v1/index/embed", type("com.sapio.knowledge.ai.InferenceKnowledgeAi$EmbedRequest"),
                    type("com.sapio.knowledge.ai.InferenceKnowledgeAi$EmbedResponse")),
            new Call("post", "/v1/study/material", StudyAi.MaterialSpec.class, StudyAi.MaterialDraft.class),
            new Call("post", "/v1/study/lesson", StudyAi.LessonSpec.class, StudyAi.LessonDraft.class),
            new Call("post", "/v1/study/questions", StudyAi.QuestionSpec.class, StudyAi.QuestionsDraft.class),
            new Call("post", "/v1/study/grade", StudyAi.GradeSpec.class, StudyAi.GradeResult.class),
            new Call("post", "/v1/study/assessment-feedback", StudyAi.FeedbackSpec.class, StudyAi.ReportNarrative.class),
            new Call("post", "/v1/study/answer", StudyAi.AnswerSpec.class, StudyAi.AnswerDraft.class),
            new Call("post", "/v1/study/notes", StudyAi.NotesSpec.class, StudyAi.NotesDraft.class));

    @BeforeAll
    static void load() throws Exception {
        openApi = JsonMapper.builder().build().readTree(Files.readString(CONTRACT));
    }

    @Test
    void contractVersionMatchesTheBackend() {
        JsonNode version = component("HealthResponse").path("properties").path("contractVersion").path("default");
        assertThat(version.asString().split("\\.")[0]).isEqualTo(InferenceCompatibilityCheck.CONTRACT_MAJOR);
    }

    @Test
    void everyCallMatchesTheContract() {
        List<String> problems = new ArrayList<>();
        for (Call call : CALLS) {
            JsonNode operation = openApi.path("paths").path(call.path()).path(call.method());
            if (operation.isMissingNode()) {
                problems.add(call.method().toUpperCase() + " " + call.path() + " is not in the contract");
                continue;
            }
            if (call.request() != null) {
                JsonNode body = operation.path("requestBody").path("content").path("application/json").path("schema");
                new Checker(problems, true).check(call.request(), body, call.path() + " request");
            }
            JsonNode ok = operation.path("responses").path("200").path("content").path("application/json")
                    .path("schema");
            new Checker(problems, false).check(call.response(), ok, call.path() + " response");
        }
        assertThat(problems).as("backend DTOs vs contracts/inference-openapi.json").isEmpty();
    }

    @Test
    void extractUploadsTheFilePart() {
        JsonNode schema = resolve(openApi.path("paths").path("/v1/extract").path("post").path("requestBody")
                .path("content").path("multipart/form-data").path("schema"));
        assertThat(names(schema.path("required"))).containsExactly("file");
    }

    @Test
    void errorEnvelopeIsWhatTheClientDecodes() {
        List<String> problems = new ArrayList<>();
        new Checker(problems, false).check(InferenceClient.ErrorBody.class, component("ErrorResponse"), "error body");
        assertThat(problems).isEmpty();
        for (Call call : CALLS) {
            if (!call.path().equals("/health")) {
                JsonNode error = openApi.path("paths").path(call.path()).path(call.method()).path("responses")
                        .path("503").path("content").path("application/json").path("schema");
                assertThat(error.path("$ref").asString()).as(call.path()).endsWith("/ErrorResponse");
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ checker

    /**
     * Requests (backend → service): every field the backend sends must exist, every required field must be sent,
     * every enum value the backend may send must be accepted. Responses (service → backend): every field the
     * backend reads must exist, and every enum value the service may return must be known to the backend.
     */
    private record Checker(List<String> problems, boolean request) {

        void check(Type type, JsonNode schema, String where) {
            JsonNode s = resolve(schema);
            Class<?> raw = rawClass(type);
            if (raw.isRecord()) {
                checkRecord(raw, s, where);
            } else if (Collection.class.isAssignableFrom(raw)) {
                expectType(s, "array", where);
                check(((ParameterizedType) type).getActualTypeArguments()[0], s.path("items"), where + "[]");
            } else if (raw.isArray() && raw != byte[].class) {
                expectType(s, "array", where);
                check(raw.getComponentType(), s.path("items"), where + "[]");
            } else if (raw.isEnum()) {
                checkEnum(raw, s, where);
            } else if (raw == String.class) {
                expectType(s, "string", where);
            } else if (raw == boolean.class || raw == Boolean.class) {
                expectType(s, "boolean", where);
            } else if (raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class) {
                expectType(s, "integer", where);
            } else if (raw == double.class || raw == Double.class || raw == float.class || raw == Float.class) {
                if (!(typeOf(s).equals("number") || typeOf(s).equals("integer"))) {
                    problems.add(where + ": backend has a number, contract has " + typeOf(s));
                }
            }
        }

        private void checkRecord(Class<?> type, JsonNode schema, String where) {
            JsonNode properties = schema.path("properties");
            Set<String> fields = Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName)
                    .collect(Collectors.toSet());
            for (RecordComponent c : type.getRecordComponents()) {
                JsonNode property = properties.path(c.getName());
                if (property.isMissingNode()) {
                    problems.add(where + "." + c.getName() + ": " + (request
                            ? "sent by the backend but unknown to the service (silently dropped)"
                            : "read by the backend but never sent by the service"));
                    continue;
                }
                check(c.getGenericType(), property, where + "." + c.getName());
            }
            if (request) {
                for (String required : names(schema.path("required"))) {
                    if (!fields.contains(required)) {
                        problems.add(where + "." + required + ": required by the service but not sent by the backend");
                    }
                }
            }
        }

        private void checkEnum(Class<?> type, JsonNode schema, String where) {
            Set<String> java = Arrays.stream(type.getEnumConstants()).map(e -> ((Enum<?>) e).name())
                    .collect(Collectors.toSet());
            Set<String> wire = new HashSet<>(names(schema.path("enum")));
            if (wire.isEmpty()) {
                problems.add(where + ": backend has enum " + type.getSimpleName() + ", contract has no enum");
                return;
            }
            Set<String> unknown = new HashSet<>(request ? java : wire);
            unknown.removeAll(request ? wire : java);
            if (!unknown.isEmpty()) {
                problems.add(where + ": values " + unknown + (request ? " are not accepted by the service"
                        : " may be returned but " + type.getSimpleName() + " does not know them"));
            }
        }

        private void expectType(JsonNode schema, String expected, String where) {
            String actual = typeOf(schema);
            if (!actual.equals(expected)) {
                problems.add(where + ": backend has " + expected + ", contract has " + actual);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** Follows {@code $ref} and unwraps {@code anyOf: [X, null]} (optional fields). */
    private static JsonNode resolve(JsonNode schema) {
        if (schema.has("$ref")) {
            String ref = schema.path("$ref").asString();
            return resolve(component(ref.substring(ref.lastIndexOf('/') + 1)));
        }
        if (schema.has("anyOf")) {
            for (JsonNode option : schema.path("anyOf")) {
                if (!"null".equals(option.path("type").asString())) {
                    return resolve(option);
                }
            }
        }
        return schema;
    }

    private static JsonNode component(String name) {
        JsonNode schema = openApi.path("components").path("schemas").path(name);
        assertThat(schema.isMissingNode()).as("schema " + name).isFalse();
        return schema;
    }

    private static String typeOf(JsonNode schema) {
        if (schema.has("type")) {
            return schema.path("type").asString();
        }
        return schema.has("properties") ? "object" : schema.has("enum") ? "string" : "any";
    }

    private static List<String> names(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asString()));
        return out;
    }

    private static Class<?> rawClass(Type type) {
        return type instanceof ParameterizedType p ? (Class<?>) p.getRawType() : (Class<?>) type;
    }

    private static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(name, e);
        }
    }
}
