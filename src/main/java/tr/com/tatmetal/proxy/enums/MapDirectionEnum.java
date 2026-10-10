package tr.com.tatmetal.proxy.enums;

import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;

import tr.com.zenergy.mini_jpa.util.IEnumNumber;

/**
 * Alan eşleştirmesinin uygulanacağı yön.
 * REQUEST : gelen istek gövdesi hedefe gönderilmeden önce,
 * RESPONSE: hedefin cevabı çağıran sisteme dönmeden önce.
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum MapDirectionEnum implements IEnumNumber, Serializable {
    REQUEST(1, "İstek", "Request"),
    RESPONSE(2, "Cevap", "Response");

    private final Integer id;
    private final String value;
    private final String engValue;

    MapDirectionEnum(int id, String value, String engValue) {
        this.id = id;
        this.value = value;
        this.engValue = engValue;
    }

    public static MapDirectionEnum get(Integer id) {
        for (MapDirectionEnum e : values()) {
            if (Objects.equals(e.getId(), id)) {
                return e;
            }
        }
        return null;
    }

    @JsonCreator
    public static MapDirectionEnum findValue(final JsonNode jsonNode) {
        String id = (jsonNode.has("id") ? jsonNode.get("id").toString() : jsonNode.toString())
                .replaceAll("\"", "");
        for (MapDirectionEnum enumRow : values()) {
            if (Objects.equals(String.valueOf(enumRow.getId()), id)
                    || enumRow.name().equalsIgnoreCase(id)) {
                return enumRow;
            }
        }
        return null;
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
