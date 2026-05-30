package com.plantops.persistence.entity;



import jakarta.persistence.Entity;

import jakarta.persistence.Lob;

import jakarta.persistence.Table;

import jakarta.persistence.UniqueConstraint;



@Entity

@Table(name = "system_parameter", uniqueConstraints = @UniqueConstraint(columnNames = {

        "workspace_id", "param_id"

}))

public class SystemParameterEntity extends WorkspaceScopedEntity {



    public String paramId;

    @Lob

    public String paramValue;

    public String description;



    public static SystemParameterEntity findByParamId(String paramId) {

        return find("workspaceId = ?1 and paramId = ?2", ws(), paramId).firstResult();

    }

    public static java.util.List<SystemParameterEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}


