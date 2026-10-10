package tr.com.tatmetal.proxy.entity;

import java.util.UUID;

import tr.com.zenergy.mini_jpa.annotation.dao.Column;
import tr.com.zenergy.mini_jpa.annotation.dao.Entity;
import tr.com.zenergy.mini_jpa.entity.BaseEntity;
import tr.com.zenergy.mini_jpa.util.TypeUtil;
import tr.com.tatmetal.proxy.enums.MapDirectionEnum;
import tr.com.tatmetal.proxy.enums.YesNoEnum;

/**
 * Bir route için kaynak alan → hedef alan eşleştirmesi.
 * Alan yolları nokta ile iç içe, [] ile dizi elemanlarını ifade eder: customer.name, items[].qty
 * Standart alanlar (id, id_serial, etag, status, ...) BaseEntity'den gelir.
 */
@Entity(tableName = "message_proxy.proxy_field_map")
public class ProxyFieldMapEntity extends BaseEntity {

    @Column(name = "proxy_route_id", isTyped = true, type = "UUID") private UUID proxyRouteId;
    @Column(name = "map_direction", isTyped = true, type = TypeUtil.I_ENUM_NUM)
    private MapDirectionEnum mapDirection;
    @Column(name = "source_field") private String sourceField;
    @Column(name = "target_field") private String targetField;
    @Column(name = "is_required", isTyped = true, type = TypeUtil.I_ENUM_NUM)
    private YesNoEnum isRequired;
    @Column(name = "default_value") private String defaultValue;
    @Column(name = "field_order") private int fieldOrder;
    @Column(name = "map_description") private String mapDescription;

    public ProxyFieldMapEntity() {
    }

    public UUID getProxyRouteId() {
        return proxyRouteId;
    }

    public void setProxyRouteId(UUID proxyRouteId) {
        this.proxyRouteId = proxyRouteId;
    }

    public MapDirectionEnum getMapDirection() {
        return mapDirection;
    }

    public void setMapDirection(MapDirectionEnum mapDirection) {
        this.mapDirection = mapDirection;
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

    public YesNoEnum getIsRequired() {
        return isRequired;
    }

    public void setIsRequired(YesNoEnum isRequired) {
        this.isRequired = isRequired;
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

    public String getMapDescription() {
        return mapDescription;
    }

    public void setMapDescription(String mapDescription) {
        this.mapDescription = mapDescription;
    }

    @Override
    public String toString() {
        return "ProxyFieldMapEntity{"
                + "proxyRouteId=" + proxyRouteId
                + ", mapDirection=" + mapDirection
                + ", sourceField='" + sourceField + '\''
                + ", targetField='" + targetField + '\''
                + ", isRequired=" + isRequired
                + ", defaultValue='" + defaultValue + '\''
                + ", fieldOrder=" + fieldOrder
                + ", mapDescription='" + mapDescription + '\''
                + '}';
    }
}
