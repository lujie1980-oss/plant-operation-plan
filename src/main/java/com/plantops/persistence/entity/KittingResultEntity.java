package com.plantops.persistence.entity;



import jakarta.persistence.Entity;

import jakarta.persistence.Table;



import java.time.LocalDateTime;

import java.util.List;



@Entity

@Table(name = "kitting_result")

public class KittingResultEntity extends WorkspaceScopedEntity {



    public LocalDateTime computedTs;

    public String salesOrderNo;

    public int salesOrderLineNo;

    public String kittingStatus;

    public String shortageReason;

    public String workOrderNo;



    public static List<KittingResultEntity> findLatest() {

        return list("workspaceId = ?1 order by computedTs desc", ws());

    }

}


