package com.plantops.masterdata;

import com.plantops.api.dto.FactoryCalendarDtos.FactoryShiftStateDto;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FactoryCalendarServiceTest {

    @Test
    void shiftDurationMinutes_sameDay() {
        assertEquals(480, FactoryCalendarService.shiftDurationMinutes(LocalTime.of(8, 0), LocalTime.of(16, 0)));
    }

    @Test
    void shiftDurationMinutes_crossMidnight() {
        assertEquals(720, FactoryCalendarService.shiftDurationMinutes(LocalTime.of(20, 0), LocalTime.of(8, 0)));
    }

    @Test
    void dailyCapacityForOwner_sumsOpenShifts() {
        List<FactoryShiftStateDto> shifts = List.of(
                new FactoryShiftStateDto("S1", "早班", "08:00", "20:00", true, 720),
                new FactoryShiftStateDto("S2", "晚班", "20:00", "08:00", false, 720));
        assertEquals(480, FactoryCalendarService.dailyCapacityForOwner(480, shifts));
    }

    @Test
    void dailyCapacityForOwner_allClosed() {
        List<FactoryShiftStateDto> shifts = List.of(
                new FactoryShiftStateDto("S1", "早班", "08:00", "20:00", false, 720),
                new FactoryShiftStateDto("S2", "晚班", "20:00", "08:00", false, 720));
        assertEquals(0, FactoryCalendarService.dailyCapacityForOwner(480, shifts));
    }
}
