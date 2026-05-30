package com.plantops.persistence.entity;



import jakarta.persistence.Entity;

import jakarta.persistence.Table;



import java.time.LocalDate;



@Entity

@Table(name = "line_opening_decision")

public class LineOpeningDecisionEntity extends WorkspaceScopedEntity {



    public String planVersionId;

    public String areaId;

    public String lineId;

    public String shiftId;

    public LocalDate calendarDate;

    public boolean opened;

    public Integer suggestedHeadcount;

}


