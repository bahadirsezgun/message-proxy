package tr.com.tatmetal.proxy.enums;

import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;

import tr.com.zenergy.mini_jpa.util.IEnumNumber;

/**
 * Bir mesaj iletiminin sonucu.
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ProxyResultEnum implements IEnumNumber, Serializable {
    SUCCESS(1, "Başarılı", "Success"),
    TARGET_ERROR(2, "Hedef Hata Döndü", "Target Error"),
    CONNECTION_ERROR(3, "Hedefe Ulaşılamadı", "Connection Error");

    private final Integer id;
    private final String value;
    private final String engValue;

    ProxyResultEnum(int id, String value, String engValue) {
        this.id = id;
        this.value = value;
        this.engValue = engValue;
    }

    public static ProxyResultEnum get(Integer id) {
        for (ProxyResultEnum e : values()) {
            if (Objects.equals(e.getId(), id)) {
                return e;
            }
        }
        return null;
    }

    /** HTTP status koduna göre sonuç tipi; 0 veya negatif = hedefe ulaşılamadı. */
    public static ProxyResultEnum fromHttpStatus(int httpStatus) {
        if (httpStatus <= 0) {
            return CONNECTION_ERROR;
        }
        return httpStatus < 400 ? SUCCESS : TARGET_ERROR;
    }

    @JsonCreator
    public static ProxyResultEnum findValue(final JsonNode jsonNode) {
        String id = (jsonNode.has("id") ? jsonNode.get("id").toString() : jsonNode.toString())
                .replaceAll("\"", "");
        for (ProxyResultEnum enumRow : values()) {
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
