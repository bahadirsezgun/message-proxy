package tr.com.tatmetal.proxy.enums;

import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;

import tr.com.zenergy.mini_jpa.util.IEnumNumber;

/**
 * Token isteği gövdesinin biçimi (auth_profile.content_type).
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum TokenContentTypeEnum implements IEnumNumber, Serializable {
    JSON(1, "application/json", "application/json"),
    FORM(2, "application/x-www-form-urlencoded", "application/x-www-form-urlencoded");

    private final Integer id;
    private final String value;
    private final String engValue;

    TokenContentTypeEnum(int id, String value, String engValue) {
        this.id = id;
        this.value = value;
        this.engValue = engValue;
    }

    public static TokenContentTypeEnum get(Integer id) {
        for (TokenContentTypeEnum e : values()) {
            if (Objects.equals(e.getId(), id)) {
                return e;
            }
        }
        return null;
    }

    @JsonCreator
    public static TokenContentTypeEnum findValue(final JsonNode jsonNode) {
        String id = (jsonNode.has("id") ? jsonNode.get("id").toString() : jsonNode.toString())
                .replaceAll("\"", "");
        for (TokenContentTypeEnum enumRow : values()) {
            if (Objects.equals(String.valueOf(enumRow.getId()), id)
                    || enumRow.name().equalsIgnoreCase(id)) {
                return enumRow;
            }
        }
        return null;
    }

    /** HTTP Content-Type header değeri. */
    public String mediaType() {
        return this.engValue;
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
