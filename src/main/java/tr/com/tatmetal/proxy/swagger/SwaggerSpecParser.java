package tr.com.tatmetal.proxy.swagger;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import tr.com.tatmetal.proxy.entity.SwaggerEndpointEntity;
import tr.com.tatmetal.proxy.enums.YesNoEnum;

/**
 * OpenAPI 3.x ve Swagger 2.0 dokümanlarındaki path + metot ikililerini
 * {@link SwaggerEndpointEntity} listesine dönüştürür. Veritabanına dokunmaz.
 */
@Component
public class SwaggerSpecParser {

    private static final String[] HTTP_METHODS = {"get", "post", "put", "patch", "delete", "head", "options", "trace"};
    private static final Pattern SERVER_VARIABLE = Pattern.compile("\\{([^}]+)}");
    private static final String NONE = "-";

    private final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * @param baseUrlOverride doluysa (ve "-" değilse) full_url için dokümandaki servers/host yerine kullanılır
     */
    public List<SwaggerEndpointEntity> parse(SwaggerDocument document, String baseUrlOverride) {
        JsonNode root = document.getRoot();
        boolean swagger2 = root.has("swagger") && !root.has("openapi");
        String baseUrl = isBlank(baseUrlOverride)
                ? resolveBaseUrl(root, document.getDocumentUri(), swagger2)
                : baseUrlOverride.trim();

        List<SwaggerEndpointEntity> result = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> paths = root.path("paths").fields();
        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = paths.next();
            String path = pathEntry.getKey();
            JsonNode pathItem = pathEntry.getValue();
            if (!pathItem.isObject()) {
                continue;
            }
            for (String method : HTTP_METHODS) {
                JsonNode operation = pathItem.get(method);
                if (operation == null || !operation.isObject()) {
                    continue;
                }
                result.add(toEntity(root, document.getGroup(), baseUrl, path, method, pathItem, operation, swagger2));
            }
        }
        return result;
    }

    private SwaggerEndpointEntity toEntity(JsonNode root, String group, String baseUrl, String path, String method,
                                           JsonNode pathItem, JsonNode operation, boolean swagger2) {
        SwaggerEndpointEntity e = new SwaggerEndpointEntity();
        e.setApiGroup(limit(defaultIfBlank(group), 100));
        e.setHttpMethod(method.toUpperCase(Locale.ROOT));
        e.setPath(limit(path, 1000));
        e.setFullUrl(limit(joinUrl(baseUrl, path), 1500));
        e.setOperationId(limit(defaultIfBlank(text(operation, "operationId")), 200));
        e.setSummary(limit(defaultIfBlank(firstNonBlank(text(operation, "summary"), text(pathItem, "summary"))), 500));
        e.setDescription(emptyToNull(firstNonBlank(text(operation, "description"), text(pathItem, "description"))));
        e.setTags(limit(defaultIfBlank(joinTextArray(operation.path("tags"))), 500));
        e.setDeprecated(operation.path("deprecated").asBoolean(false) ? YesNoEnum.YES : YesNoEnum.NO);

        ArrayNode parameters = mergeParameters(pathItem.path("parameters"), operation.path("parameters"));
        if (swagger2) {
            // Swagger 2: gövde "in: body" parametresi olarak, içerik tipi consumes ile gelir
            ArrayNode nonBody = jsonMapper.createArrayNode();
            JsonNode bodyParam = null;
            boolean hasFormData = false;
            for (JsonNode p : parameters) {
                String in = p.path("in").asText("");
                if ("body".equals(in)) {
                    bodyParam = p;
                } else {
                    hasFormData |= "formData".equals(in);
                    nonBody.add(p);
                }
            }
            e.setParametersJson(toJson(nonBody));
            e.setRequestBodyJson(bodyParam == null ? null : toJson(bodyParam));
            if (bodyParam != null || hasFormData) {
                JsonNode consumes = operation.has("consumes") ? operation.path("consumes") : root.path("consumes");
                e.setRequestContentType(limit(defaultIfBlank(joinTextArray(consumes)), 500));
            } else {
                e.setRequestContentType(NONE);
            }
        } else {
            e.setParametersJson(toJson(parameters));
            JsonNode requestBody = operation.get("requestBody");
            e.setRequestBodyJson(requestBody == null ? null : toJson(requestBody));
            e.setRequestContentType(limit(defaultIfBlank(
                    requestBody == null ? null : joinFieldNames(requestBody.path("content"))), 500));
        }
        JsonNode responses = operation.get("responses");
        e.setResponsesJson(responses == null ? null : toJson(responses));
        return e;
    }

    // ------------------------------------------------------------------
    // Taban adres
    // ------------------------------------------------------------------

    /**
     * OpenAPI 3: servers[0].url (değişkenler default değerle doldurulur, göreli ise doküman adresine göre çözülür).
     * Swagger 2: schemes[0] + host + basePath (eksik olanlar doküman adresinden tamamlanır).
     */
    String resolveBaseUrl(JsonNode root, URI documentUri, boolean swagger2) {
        String origin = documentUri.getScheme() + "://" + documentUri.getRawAuthority();
        if (swagger2) {
            String scheme = root.path("schemes").isArray() && root.path("schemes").size() > 0
                    ? root.path("schemes").get(0).asText(documentUri.getScheme())
                    : documentUri.getScheme();
            String host = isBlank(text(root, "host")) ? documentUri.getRawAuthority() : text(root, "host");
            String basePath = isBlank(text(root, "basePath")) ? "" : text(root, "basePath");
            return trimTrailingSlash(scheme + "://" + host + basePath);
        }
        JsonNode servers = root.path("servers");
        if (!servers.isArray() || servers.size() == 0 || isBlank(text(servers.get(0), "url"))) {
            return origin;
        }
        JsonNode server = servers.get(0);
        String url = substituteVariables(text(server, "url"), server.path("variables"));
        try {
            URI serverUri = URI.create(url);
            return trimTrailingSlash(serverUri.isAbsolute() ? serverUri.toString()
                    : documentUri.resolve(serverUri).toString());
        } catch (IllegalArgumentException ex) {
            return origin;
        }
    }

    private String substituteVariables(String url, JsonNode variables) {
        Matcher m = SERVER_VARIABLE.matcher(url);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = variables.path(m.group(1)).path("default").asText("");
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String joinUrl(String baseUrl, String path) {
        String base = trimTrailingSlash(baseUrl == null ? "" : baseUrl);
        if (path == null || path.isEmpty()) {
            return base;
        }
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    // ------------------------------------------------------------------
    // Parametre / JSON yardımcıları
    // ------------------------------------------------------------------

    /** Path seviyesindeki parametreler + operasyon parametreleri; aynı (name, in) ise operasyondaki geçerlidir. */
    private ArrayNode mergeParameters(JsonNode pathLevel, JsonNode operationLevel) {
        Map<String, JsonNode> merged = new LinkedHashMap<>();
        for (JsonNode source : new JsonNode[]{pathLevel, operationLevel}) {
            if (!source.isArray()) {
                continue;
            }
            int index = 0;
            for (JsonNode p : source) {
                String key = p.has("$ref")
                        ? "ref:" + p.path("$ref").asText()
                        : p.path("in").asText("") + ":" + p.path("name").asText("#" + index);
                merged.put(key, p);
                index++;
            }
        }
        ArrayNode result = jsonMapper.createArrayNode();
        merged.values().forEach(result::add);
        return result;
    }

    private String toJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()
                || (node.isContainerNode() && node.size() == 0)) {
            return null;
        }
        try {
            return jsonMapper.writeValueAsString(node);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String joinTextArray(JsonNode array) {
        if (!array.isArray()) {
            return null;
        }
        Set<String> values = new LinkedHashSet<>();
        array.forEach(v -> {
            if (!v.asText("").isBlank()) {
                values.add(v.asText());
            }
        });
        return values.isEmpty() ? null : String.join(",", values);
    }

    private static String joinFieldNames(JsonNode object) {
        if (!object.isObject() || object.size() == 0) {
            return null;
        }
        List<String> names = new ArrayList<>();
        object.fieldNames().forEachRemaining(names::add);
        return String.join(",", names);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String firstNonBlank(String a, String b) {
        return !isBlank(a) ? a : b;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank() || NONE.equals(value.trim());
    }

    private static String defaultIfBlank(String value) {
        return value == null || value.isBlank() ? NONE : value.trim();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
