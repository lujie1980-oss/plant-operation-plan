package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalTime;

@Entity
@Table(name = "factory_calendar_policy")
public class FactoryCalendarPolicyEntity extends WorkspaceScopedEntity {

    @Column(name = "saturday_work")
    public boolean saturdayWork;

    @Column(name = "sunday_work")
    public boolean sundayWork;

    /** TWO or THREE */
    @Column(name = "shift_mode")
    public String shiftMode;

    @Column(name = "shift1_start")
    public LocalTime shift1Start;

    @Column(name = "shift1_end")
    public LocalTime shift1End;

    @Column(name = "shift2_start")
    public LocalTime shift2Start;

    @Column(name = "shift2_end")
    public LocalTime shift2End;

    @Column(name = "shift3_start")
    public LocalTime shift3Start;

    @Column(name = "shift3_end")
    public LocalTime shift3End;

    public static FactoryCalendarPolicyEntity findForWorkspace() {
        return find("workspaceId", ws()).firstResult();
    }
}
