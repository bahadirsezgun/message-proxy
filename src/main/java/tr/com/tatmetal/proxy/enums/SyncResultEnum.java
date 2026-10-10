package tr.com.tatmetal.proxy.enums;

import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;

import tr.com.zenergy.mini_jpa.util.IEnumNumber;

/**
 * Swagger senkronizasyonunun sonucu.
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum SyncResultEnum implements IEnumNumber, Serializable {
    NEVER(0, "Çalışmadı", "Never"),
    SUCCESS(1, "Başarılı", "Success"),
    ERROR(2, "Hata", "Error");

    private final Integer id;
    private final String value;
    private final String engValue;

    SyncResultEnum(int id, String value, String engValue) {
        this.id = id;
        this.value = value;
        this.engValue = engValue;
    }

    public static SyncResultEnum get(Integer id) {
        for (SyncResultEnum e : values()) {
            if (Objects.equals(e.getId(), id)) {
                return e;
            }
        }
        return null;
    }

    @JsonCreator
    public static SyncResultEnum findValue(final JsonNode jsonNode) {
        String id = (jsonNode.has("id") ? jsonNode.get("id").toString() : jsonNode.toString())
                .replaceAll("\"", "");
        for (SyncResultEnum enumRow : values()) {
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
