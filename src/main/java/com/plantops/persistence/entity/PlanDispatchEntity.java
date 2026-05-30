package com.plantops.persistence.entity;



import jakarta.persistence.Entity;

import jakarta.persistence.Table;



import java.time.LocalDateTime;



@Entity

@Table(name = "plan_dispatch")

public class PlanDispatchEntity extends WorkspaceScopedEntity {



    public String planVersionId;

    public LocalDateTime dispatchedTs;

    public String targetSystem;

}


