package tr.com.tatmetal.proxy.enums;

import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;

import tr.com.zenergy.mini_jpa.util.IEnumNumber;

/**
 * Hedef sisteme iletimde kullanılacak HTTP metodu.
 * PASS_THROUGH: gelen isteğin metodu aynen kullanılır.
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum HttpMethodEnum implements IEnumNumber, Serializable {
    PASS_THROUGH(0, "Gelen Metot", "Pass Through"),
    GET(1, "GET", "GET"),
    POST(2, "POST", "POST"),
    PUT(3, "PUT", "PUT"),
    PATCH(4, "PATCH", "PATCH"),
    DELETE(5, "DELETE", "DELETE");

    private final Integer id;
    private final String value;
    private final String engValue;

    HttpMethodEnum(int id, String value, String engValue) {
        this.id = id;
        this.value = value;
        this.engValue = engValue;
    }

    public static HttpMethodEnum get(Integer id) {
        for (HttpMethodEnum e : values()) {
            if (Objects.equals(e.getId(), id)) {
                return e;
            }
        }
        return null;
    }

    @JsonCreator
    public static HttpMethodEnum findValue(final JsonNode jsonNode) {
        String id = (jsonNode.has("id") ? jsonNode.get("id").toString() : jsonNode.toString())
                .replaceAll("\"", "");
        for (HttpMethodEnum enumRow : values()) {
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
