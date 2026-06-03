package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "factory_calendar_day_override")
public class FactoryCalendarDayOverrideEntity extends WorkspaceScopedEntity {

    @Column(name = "calendar_date")
    public LocalDate calendarDate;

    @Column(name = "shift1_open")
    public boolean shift1Open;

    @Column(name = "shift2_open")
    public boolean shift2Open;

    @Column(name = "shift3_open")
    public Boolean shift3Open;

    public static FactoryCalendarDayOverrideEntity findByDate(LocalDate date) {
        return find("workspaceId = ?1 and calendarDate = ?2", ws(), date).firstResult();
    }

    public static List<FactoryCalendarDayOverrideEntity> findBetween(LocalDate from, LocalDate to) {
        return list("workspaceId = ?1 and calendarDate >= ?2 and calendarDate <= ?3", ws(), from, to);
    }
}
