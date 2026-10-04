package tr.com.tatmetal.proxy.model;

import java.io.Serializable;

/**
 * Redis'te tutulan, sadeleştirilmiş alan eşleştirme tanımı (proxy_field_map satırı).
 * Entity yerine ayrı sınıf kullanılır; böylece cache içeriği mini_jpa/BaseEntity yapısından bağımsızdır.
 */
public class ProxyFieldMapDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sourceField;
    private String targetField;
    private boolean required;
    private String defaultValue;
    private int fieldOrder;

    public ProxyFieldMapDefinition() {
    }

    public ProxyFieldMapDefinition(String sourceField, String targetField, boolean required,
                                   String defaultValue, int fieldOrder) {
        this.sourceField = sourceField;
        this.targetField = targetField;
        this.required = required;
        this.defaultValue = defaultValue;
        this.fieldOrder = fieldOrder;
    }

    public String getSourceField() {
        return sourceField;
    }

    public void setSourceField(String sourceField) {
        this.sourceField = sourceField;
    }

    public String getTargetField() {
        return targetField;
    }

    public void setTargetField(String targetField) {
        this.targetField = targetField;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public int getFieldOrder() {
        return fieldOrder;
    }

    public void setFieldOrder(int fieldOrder) {
        this.fieldOrder = fieldOrder;
    }

    @Override
    public String toString() {
        return "ProxyFieldMapDefinition{"
                + "sourceField='" + sourceField + '\''
                + ", targetField='" + targetField + '\''
                + ", required=" + required
                + ", defaultValue='" + defaultValue + '\''
                + ", fieldOrder=" + fieldOrder
                + '}';
    }
}
