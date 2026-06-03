export interface FactoryCalendarPolicy {
  id?: number | null;
  saturdayWork: boolean;
  sundayWork: boolean;
  shiftMode: 'TWO' | 'THREE';
  shift1Start: string;
  shift1End: string;
  shift2Start: string;
  shift2End: string;
  shift3Start: string;
  shift3End: string;
}

export interface FactoryShiftState {
  shiftId: string;
  label: string;
  start: string;
  end: string;
  open: boolean;
  capacityMinutes: number;
}

export interface FactoryCalendarDay {
  date: string;
  dayOfWeek: number;
  weekend: boolean;
  hasOverride: boolean;
  workDay: boolean;
  shifts: FactoryShiftState[];
  openShiftCount: number;
  totalCapacityMinutes: number;
  status: 'CLOSED' | 'PARTIAL' | 'FULL';
}

export interface FactoryCalendarMonth {
  year: number;
  month: number;
  policy: FactoryCalendarPolicy;
  days: FactoryCalendarDay[];
}

export interface FactoryDayOverrideRequest {
  date: string;
  shift1Open: boolean;
  shift2Open: boolean;
  shift3Open?: boolean | null;
  clearOverride: boolean;
}

export interface FactoryCalendarSyncResult {
  horizonDays: number;
  resourceOwnerCount: number;
  fromDate: string;
  toDate: string;
}
