package tr.com.tatmetal.proxy.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import tr.com.tatmetal.proxy.exception.ProxyMappingException;
import tr.com.tatmetal.proxy.model.ProxyFieldMapDefinition;

/**
 * JSON mesaj alanlarını proxy_field_map tanımlarına göre yeniden adlandırır / taşır.
 * <p>
 * Alan yolu söz dizimi:
 * <ul>
 *   <li>{@code customerName}            → üst seviye alan</li>
 *   <li>{@code customer.name}           → iç içe alan (ara nesneler hedefte yoksa oluşturulur)</li>
 *   <li>{@code items[].qty}             → dizideki her elemanın alanı</li>
 *   <li>{@code items[]}                 → dizinin tamamı (sadece dizi adı değişir)</li>
 *   <li>{@code orders[].lines[].code}   → iç içe diziler</li>
 * </ul>
 * Kaynak ve hedef yolda {@code []} sayısı aynı olmalıdır.
 * <p>
 * passUnmappedFields = true  → kaynak mesaj kopyalanır, eşleştirilen alanlar eski yerinden silinip yeni yerine yazılır.
 * passUnmappedFields = false → hedef mesaj boş başlar, sadece eşleştirilen alanlar yazılır.
 * <p>
 * Gövde en üstte dizi ise ({@code [ {...}, {...} ]}) eşleştirme her elemana ayrı uygulanır.
 */
@Service
public class ProxyFieldMappingService {

    private static final String ARRAY_MARK = "[]";

    private final ObjectMapper objectMapper;

    public ProxyFieldMappingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode map(JsonNode source, List<ProxyFieldMapDefinition> fieldMaps, boolean passUnmappedFields) {
        if (source == null || fieldMaps == null || fieldMaps.isEmpty()) {
            return source;
        }
        List<FieldRule> rules = new ArrayList<>();
        for (ProxyFieldMapDefinition fieldMap : fieldMaps) {
            validate(fieldMap.getSourceField(), fieldMap.getTargetField());
            rules.add(new FieldRule(fieldMap.getSourceField(), fieldMap.getTargetField(),
                    fieldMap.isRequired(), fieldMap.getDefaultValue()));
        }
        if (source.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            for (int i = 0; i < source.size(); i++) {
                result.add(mapNode(source.get(i), rules, passUnmappedFields, "[" + i + "]"));
            }
            return result;
        }
        return mapNode(source, rules, passUnmappedFields, "");
    }

    /** Tanım geçerliliği kontrolü; proxy_field_map kaydedilirken de kullanılır. */
    public static void validate(String sourceField, String targetField) {
        if (isBlank(sourceField) || isBlank(targetField)) {
            throw new ProxyMappingException("source_field ve target_field boş olamaz");
        }
        for (String path : new String[]{sourceField, targetField}) {
            String trimmed = path.trim();
            if (trimmed.startsWith(".") || trimmed.endsWith(".") || trimmed.contains("..")
                    || trimmed.startsWith(ARRAY_MARK)) {
                throw new ProxyMappingException("Geçersiz alan yolu: " + path);
            }
            String withoutArrays = trimmed.replace(ARRAY_MARK, "");
            if (withoutArrays.contains("[") || withoutArrays.contains("]")) {
                throw new ProxyMappingException("Dizi için sadece [] kullanılabilir (indeks desteklenmez): " + path);
            }
        }
        if (countArrays(sourceField) != countArrays(targetField)) {
            throw new ProxyMappingException("Kaynak ve hedef yolda [] sayısı aynı olmalı: "
                    + sourceField + " → " + targetField);
        }
    }

    // ------------------------------------------------------------------
    // Eşleştirme
    // ------------------------------------------------------------------

    private JsonNode mapNode(JsonNode source, List<FieldRule> rules, boolean passUnmapped, String context) {
        if (source == null || source.isNull()) {
            return source;
        }
        if (!source.isObject()) {
            throw new ProxyMappingException(label(context) + " nesne (JSON object) bekleniyordu");
        }
        ObjectNode result = passUnmapped ? source.deepCopy() : objectMapper.createObjectNode();

        List<FieldRule> simpleRules = new ArrayList<>();
        Map<String, List<FieldRule>> arrayGroups = new LinkedHashMap<>();
        for (FieldRule rule : rules) {
            int sourceIdx = rule.source.indexOf(ARRAY_MARK);
            if (sourceIdx < 0) {
                simpleRules.add(rule);
            } else {
                int targetIdx = rule.target.indexOf(ARRAY_MARK);
                String groupKey = rule.source.substring(0, sourceIdx) + "\u0000" + rule.target.substring(0, targetIdx);
                arrayGroups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(rule);
            }
        }

        // 1) Tüm değerler değiştirilmemiş kaynaktan okunur (alan takası gibi durumlar doğru çalışsın)
        List<String> writeTargets = new ArrayList<>();
        List<JsonNode> writeValues = new ArrayList<>();
        List<String> removeSources = new ArrayList<>();

        for (FieldRule rule : simpleRules) {
            JsonNode value = get(source, rule.source);
            removeSources.add(rule.source);
            if (value == null || value.isMissingNode()) {
                if (rule.defaultValue != null) {
                    value = parseDefault(rule.defaultValue);
                } else if (rule.required) {
                    throw new ProxyMappingException("Zorunlu alan bulunamadı: " + label(context, rule.source));
                } else {
                    continue;
                }
            }
            writeTargets.add(rule.target);
            writeValues.add(value.deepCopy());
        }

        for (List<FieldRule> group : arrayGroups.values()) {
            FieldRule first = group.get(0);
            String sourceArrayPath = first.source.substring(0, first.source.indexOf(ARRAY_MARK));
            String targetArrayPath = first.target.substring(0, first.target.indexOf(ARRAY_MARK));
            removeSources.add(sourceArrayPath);

            List<FieldRule> elementRules = new ArrayList<>();
            boolean anyRequired = false;
            for (FieldRule rule : group) {
                anyRequired |= rule.required;
                String sourceRest = restAfterArray(rule.source);
                String targetRest = restAfterArray(rule.target);
                if (!sourceRest.isEmpty() || !targetRest.isEmpty()) {
                    if (sourceRest.isEmpty() || targetRest.isEmpty()) {
                        throw new ProxyMappingException("Dizi elemanı ile alan eşleştirilemez: "
                                + rule.source + " → " + rule.target);
                    }
                    elementRules.add(new FieldRule(sourceRest, targetRest, rule.required, rule.defaultValue));
                }
            }

            JsonNode array = get(source, sourceArrayPath);
            if (array == null || array.isMissingNode() || array.isNull()) {
                if (anyRequired) {
                    throw new ProxyMappingException("Zorunlu dizi bulunamadı: " + label(context, sourceArrayPath));
                }
                continue;
            }
            if (!array.isArray()) {
                throw new ProxyMappingException(label(context, sourceArrayPath) + " dizi (JSON array) bekleniyordu");
            }
            ArrayNode mappedArray = objectMapper.createArrayNode();
            for (int i = 0; i < array.size(); i++) {
                JsonNode element = array.get(i);
                if (elementRules.isEmpty()) {
                    mappedArray.add(element.deepCopy());
                } else {
                    mappedArray.add(mapNode(element, elementRules, passUnmapped,
                            context + (context.isEmpty() ? "" : ".") + sourceArrayPath + "[" + i + "]"));
                }
            }
            writeTargets.add(targetArrayPath);
            writeValues.add(mappedArray);
        }

        // 2) Eşleşmeyen alanlar aktarılıyorsa eşleştirilen alanlar eski yerinden kaldırılır
        if (passUnmapped) {
            for (String path : removeSources) {
                remove(result, path);
            }
        }

        // 3) Değerler hedef yollarına yazılır
        for (int i = 0; i < writeTargets.size(); i++) {
            set(result, writeTargets.get(i), writeValues.get(i), context);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // JSON yol yardımcıları
    // ------------------------------------------------------------------

    private JsonNode get(JsonNode node, String path) {
        JsonNode current = node;
        for (String segment : path.split("\\.")) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    private void set(ObjectNode root, String path, JsonNode value, String context) {
        String[] segments = path.split("\\.");
        ObjectNode current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            JsonNode child = current.get(segments[i]);
            if (child == null || child.isNull()) {
                current = current.putObject(segments[i]);
            } else if (child.isObject()) {
                current = (ObjectNode) child;
            } else {
                throw new ProxyMappingException("Hedef yol yazılamadı, '" + segments[i]
                        + "' nesne değil: " + label(context, path));
            }
        }
        current.set(segments[segments.length - 1], value);
    }

    /** Alanı siler; silme sonrası boş kalan ara nesneleri de temizler. */
    private void remove(ObjectNode root, String path) {
        String[] segments = path.split("\\.");
        List<ObjectNode> parents = new ArrayList<>();
        ObjectNode current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            JsonNode child = current.get(segments[i]);
            if (child == null || !child.isObject()) {
                return;
            }
            parents.add(current);
            current = (ObjectNode) child;
        }
        current.remove(segments[segments.length - 1]);
        for (int i = parents.size() - 1; i >= 0; i--) {
            ObjectNode child = (ObjectNode) parents.get(i).get(segments[i]);
            if (child.size() > 0) {
                break;
            }
            parents.get(i).remove(segments[i]);
        }
    }

    /** default_value JSON olarak yorumlanır (10, true, "abc", {...}); geçerli JSON değilse metin kabul edilir. */
    private JsonNode parseDefault(String defaultValue) {
        try {
            return objectMapper.readTree(defaultValue);
        } catch (JsonProcessingException e) {
            return TextNode.valueOf(defaultValue);
        }
    }

    private static String restAfterArray(String path) {
        String rest = path.substring(path.indexOf(ARRAY_MARK) + ARRAY_MARK.length());
        return rest.startsWith(".") ? rest.substring(1) : rest;
    }

    private static int countArrays(String path) {
        int count = 0;
        int idx = path.indexOf(ARRAY_MARK);
        while (idx >= 0) {
            count++;
            idx = path.indexOf(ARRAY_MARK, idx + ARRAY_MARK.length());
        }
        return count;
    }

    private static String label(String context) {
        return context.isEmpty() ? "Gövde" : context;
    }

    private static String label(String context, String path) {
        return context.isEmpty() ? path : context + "." + path;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class FieldRule {
        private final String source;
        private final String target;
        private final boolean required;
        private final String defaultValue;

        private FieldRule(String source, String target, boolean required, String defaultValue) {
            this.source = source.trim();
            this.target = target.trim();
            this.required = required;
            this.defaultValue = defaultValue;
        }
    }
}
