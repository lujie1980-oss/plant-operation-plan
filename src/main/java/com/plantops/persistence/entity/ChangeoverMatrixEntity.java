package com.plantops.persistence.entity;



import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "changeover_matrix", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "operation_name", "attribute_key", "from_attribute_value", "to_attribute_value"
}))
public class ChangeoverMatrixEntity extends WorkspaceScopedEntity {

    @Column(name = "operation_name")
    public String operationName;

    @Column(name = "attribute_key")
    public String attributeKey;

    @Column(name = "from_attribute_value")
    public String fromAttributeValue;

    @Column(name = "to_attribute_value")
    public String toAttributeValue;

    public int setupMinutes;

    public static java.util.List<ChangeoverMatrixEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static java.util.List<ChangeoverMatrixEntity> findByOperation(String operationName) {
        return list("workspaceId = ?1 and operationName = ?2 order by attributeKey, fromAttributeValue, toAttributeValue",
                ws(), operationName);
    }

    public static ChangeoverMatrixEntity findEntry(
            String operationName, String attributeKey, String fromValue, String toValue) {
        return find(
                "workspaceId = ?1 and operationName = ?2 and attributeKey = ?3 "
                        + "and fromAttributeValue = ?4 and toAttributeValue = ?5",
                ws(),
                operationName,
                attributeKey,
                fromValue,
                toValue).firstResult();
    }
}
