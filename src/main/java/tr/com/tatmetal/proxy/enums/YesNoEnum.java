package tr.com.tatmetal.proxy.enums;

import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;

import tr.com.zenergy.mini_jpa.util.IEnumNumber;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum YesNoEnum implements IEnumNumber, Serializable {
    NO(0, "Hayır", "No"),
    YES(1, "Evet", "Yes");

    private final Integer id;
    private final String value;
    private final String engValue;

    YesNoEnum(int id, String value, String engValue) {
        this.id = id;
        this.value = value;
        this.engValue = engValue;
    }

    public static YesNoEnum get(Integer id) {
        for (YesNoEnum e : values()) {
            if (Objects.equals(e.getId(), id)) {
                return e;
            }
        }
        return null;
    }

    @JsonCreator
    public static YesNoEnum findValue(final JsonNode jsonNode) {
        // Handle boolean values
        if (jsonNode.isBoolean()) {
            return jsonNode.asBoolean() ? YES : NO;
        }
        String id = (jsonNode.has("id") ? jsonNode.get("id").toString() : jsonNode.toString())
                .replaceAll("\"", "");
        // Handle "true"/"false" strings
        if ("true".equalsIgnoreCase(id)) {
            return YES;
        }
        if ("false".equalsIgnoreCase(id)) {
            return NO;
        }
        for (YesNoEnum enumRow : values()) {
            if (Objects.equals(String.valueOf(enumRow.getId()), id)) {
                return enumRow;
            }
        }
        return null;
    }

    public static YesNoEnum fromBoolean(Boolean value) {
        return (value != null && value) ? YES : NO;
    }

    public Boolean toBoolean() {
        return this == YES;
    }

    public int getId() {
        return this.id;
    }

    public String getValue() {
        return this.value;
    }

    public String getEngValue() {
        return this.engValue;
    }

    @Override
    public Integer id() {
        return this.id;
    }
}
