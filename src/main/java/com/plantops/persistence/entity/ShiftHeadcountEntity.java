package com.plantops.persistence.entity;



import jakarta.persistence.Entity;

import jakarta.persistence.Table;

import jakarta.persistence.UniqueConstraint;



import java.time.LocalDate;



@Entity

@Table(name = "shift_headcount", uniqueConstraints = @UniqueConstraint(columnNames = {

        "workspace_id", "area_id", "shift_id", "calendar_date"

}))

public class ShiftHeadcountEntity extends WorkspaceScopedEntity {



    public String areaId;

    public String shiftId;

    public LocalDate calendarDate;

    public int availableHeadcount;

    public static java.util.List<ShiftHeadcountEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}


