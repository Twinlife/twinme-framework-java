package org.twinlife.twinme.models.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.twinlife.twinme.models.Capabilities;

import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.TimeZone;

class ScheduleTest {

    private static final TimeZone UTC = TimeZone.getTimeZone(ZoneOffset.UTC);
    private static final Time EIGHT_AM = new Time(8, 0);
    private static final Time NINE_AM = new Time(9, 0);
    private static final Time TEN_AM = new Time(10, 0);
    private static final Time SIX_PM = new Time(18, 0);
    private static final Time EIGHT_PM = new Time(20, 0);

    private static final Date CHRISTMAS_2023 = new Date(2023, 12, 25);

    private static final DateTime MONDAY_NINE_AM = new DateTime(CHRISTMAS_2023, NINE_AM);

    private static final DateTime MONDAY_SIX_PM = new DateTime(CHRISTMAS_2023, SIX_PM);
    private static final Date NYE_2024 = new Date(2023, 12, 31);

    private static final DateTime SUNDAY_NINE_AM = new DateTime(NYE_2024, NINE_AM);

    private static final TimeRange MONDAY_8AM_TO_10AM = new WeeklyTimeRange(WeeklyTimeRange.DayOfWeek.MONDAY, EIGHT_AM, TEN_AM);

    private static final TimeRange THURSDAY_6PM_TO_8PM = new WeeklyTimeRange(WeeklyTimeRange.DayOfWeek.THURSDAY, SIX_PM, EIGHT_PM);

    private static final TimeRange CHRISTMAS_TO_NYE = new DateTimeRange(new DateTime(CHRISTMAS_2023, EIGHT_AM), new DateTime(NYE_2024, SIX_PM));

    private static final TimeRange CHRISTMAS_2023_EIGHT_AM_TO_TEN_AM = new DateTimeRange(new DateTime(CHRISTMAS_2023, EIGHT_AM), new DateTime(CHRISTMAS_2023, TEN_AM));

    private final TimeRange oneHourBeforeNowToOneHourAfter;
    private final TimeRange oneHourAfterNowToTwoHoursAfter;


    private final TimeRange weeklyOneHourBeforeNowToOneHourAfter;
    private final TimeRange weeklyOneHourAfterNowToTwoHoursAfter;

    //
    // Test dates
    //
    private static final Calendar NYE_2024_PLUS_ONE_DAY = new DateTime(NYE_2024, SIX_PM).toCalendar(UTC);
    static {
        NYE_2024_PLUS_ONE_DAY.add(Calendar.DAY_OF_MONTH, 1);
    }

    private static final Calendar NYE_2024_PLUS_ONE_HOUR = new DateTime(NYE_2024, SIX_PM).toCalendar(UTC);
    static {
        NYE_2024_PLUS_ONE_HOUR.add(Calendar.HOUR_OF_DAY, 1);
    }

    private static final Calendar CHRISTMAS_2023_PLUS_ONE_DAY = new DateTime(CHRISTMAS_2023, EIGHT_AM).toCalendar(UTC);
    static {
        CHRISTMAS_2023_PLUS_ONE_DAY.add(Calendar.DAY_OF_MONTH, 1);
    }

    private static final Calendar CHRISTMAS_2023_PLUS_ONE_HOUR = new DateTime(CHRISTMAS_2023, EIGHT_AM).toCalendar(UTC);
    static {
        CHRISTMAS_2023_PLUS_ONE_HOUR.add(Calendar.HOUR_OF_DAY, 1);
    }

    private static final Calendar CHRISTMAS_2023_SEVEN_AM = new DateTime(CHRISTMAS_2023, EIGHT_AM).toCalendar(UTC);
    static {
        CHRISTMAS_2023_SEVEN_AM.add(Calendar.HOUR_OF_DAY, -1);
    }

    private static final Calendar CHRISTMAS_2023_NINE_AM = new DateTime(CHRISTMAS_2023, EIGHT_AM).toCalendar(UTC);
    static {
        CHRISTMAS_2023_NINE_AM.add(Calendar.HOUR_OF_DAY, 1);
    }

    private static final Calendar CHRISTMAS_2023_ELEVEN_AM = new DateTime(CHRISTMAS_2023, TEN_AM).toCalendar(UTC);
    static {
        CHRISTMAS_2023_ELEVEN_AM.add(Calendar.HOUR_OF_DAY, 1);
    }

    //
    // Schedules
    //
    private static final Schedule weeklySchedule = new Schedule(false, UTC,
            Arrays.asList(MONDAY_8AM_TO_10AM, THURSDAY_6PM_TO_8PM));

    private static final Schedule dateTimeSchedule = new Schedule( false, UTC,
            Collections.singletonList(CHRISTMAS_TO_NYE));

    //
    // Serialized schedules
    //
    private static final String serializedWeeklySchedule = "en=1;tz=UTC;tr=weekly,1,08:00,10:00;tr=weekly,4,18:00,20:00";
    private static final String serializedDateTimeSchedule = "en=1;tz=UTC;tr=dateTime,2023-12-25T08:00,2023-12-31T18:00";
    private static final String serializedDisabledDateTimeSchedule = "en=0;tz=UTC;tr=dateTime,2023-12-25T08:00,2023-12-31T18:00";

    public ScheduleTest(){
        Calendar now = Calendar.getInstance(UTC);

        WeeklyTimeRange.DayOfWeek dayOfWeek = WeeklyTimeRange.DayOfWeek.from(now);

        DateTime nowPlusOneHour = calendarToDateTime(now, 1);
        DateTime nowMinusOneHour = calendarToDateTime(now, -1);
        DateTime nowPlusTwoHours = calendarToDateTime(now, 2);

        oneHourBeforeNowToOneHourAfter = new DateTimeRange(nowMinusOneHour, nowPlusOneHour);
        oneHourAfterNowToTwoHoursAfter = new DateTimeRange(nowPlusOneHour, nowPlusTwoHours);

        weeklyOneHourBeforeNowToOneHourAfter = new WeeklyTimeRange(dayOfWeek, nowMinusOneHour.time, nowPlusOneHour.time);
        weeklyOneHourAfterNowToTwoHoursAfter = new WeeklyTimeRange(dayOfWeek, nowPlusOneHour.time, nowPlusTwoHours.time);
    }

    private DateTime calendarToDateTime(Calendar calendar, int hours){
        Calendar cal = (Calendar) calendar.clone();
        cal.add(Calendar.HOUR_OF_DAY, hours);
        Time time = new Time(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        Date date = new Date(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)+1, cal.get(Calendar.DAY_OF_MONTH));
        return new DateTime(date, time);
    }

    @Test
    void weeklyToCapabilities() {
        assertEquals(serializedWeeklySchedule, weeklySchedule.toCapability());
    }

    @Test
    void weeklyInCapabilitiesObject(){
        Capabilities caps = new Capabilities();
        caps.setSchedule(weeklySchedule);

        Schedule capSchedule = caps.getSchedule();

        assertSchedule(weeklySchedule, capSchedule);
    }

    @Test
    void weeklyOfCapabilities() {
        Schedule parsedSchedule = Schedule.ofCapability(serializedWeeklySchedule);

        assertSchedule(weeklySchedule, parsedSchedule);
    }

    @Test
    void dateTimeToCapabilities() {
        assertEquals(serializedDateTimeSchedule, dateTimeSchedule.toCapability());
    }

    @Test
    void disabledDateTimeToCapabilities() {
        Schedule parsedSchedule = Schedule.ofCapability(serializedDateTimeSchedule);
        assertNotNull(parsedSchedule);
        parsedSchedule.setEnabled(false);
        assertEquals(serializedDisabledDateTimeSchedule, parsedSchedule.toCapability());
    }

    @Test
    void dateTimeInCapabilitiesObject(){
        Capabilities caps = new Capabilities();
        caps.setSchedule(dateTimeSchedule);

        Schedule capSchedule = caps.getSchedule();

        assertSchedule(dateTimeSchedule, capSchedule);
    }

    @Test
    void dateTimeOfCapabilities() {
        Schedule parsedSchedule = Schedule.ofCapability(serializedDateTimeSchedule);

        assertSchedule(dateTimeSchedule, parsedSchedule);
    }

    @Test
    void dateTimeIsInRange() {
        assertTrue(CHRISTMAS_TO_NYE.isTimestampInRange(CHRISTMAS_2023_PLUS_ONE_DAY.getTimeInMillis(), UTC));
        assertTrue(CHRISTMAS_TO_NYE.isTimestampInRange(CHRISTMAS_2023_PLUS_ONE_HOUR.getTimeInMillis(), UTC));

        assertTrue(CHRISTMAS_2023_EIGHT_AM_TO_TEN_AM.isTimestampInRange(CHRISTMAS_2023_NINE_AM.getTimeInMillis(), UTC));
    }

    @Test
    void dateTimeNowIsInRange() {
        Schedule schedule = new Schedule( false, UTC, Collections.singletonList(oneHourBeforeNowToOneHourAfter));
        assertTrue(schedule.isNowInRange());
    }

    @Test
    void dateTimeIsNotInRange() {
        assertFalse(CHRISTMAS_TO_NYE.isTimestampInRange(NYE_2024_PLUS_ONE_DAY.getTimeInMillis(), UTC));

        assertFalse(CHRISTMAS_2023_EIGHT_AM_TO_TEN_AM.isTimestampInRange(CHRISTMAS_2023_SEVEN_AM.getTimeInMillis(), UTC));

        assertFalse(CHRISTMAS_2023_EIGHT_AM_TO_TEN_AM.isTimestampInRange(CHRISTMAS_2023_ELEVEN_AM.getTimeInMillis(), UTC));
    }

    @Test
    void dateTimeNowIsNotInRange() {
        Schedule schedule = new Schedule(false, UTC, Collections.singletonList(oneHourAfterNowToTwoHoursAfter));
        assertFalse(schedule.isNowInRange());
    }

    @Test
    void weeklyIsInRange() {
        assertTrue(MONDAY_8AM_TO_10AM.isTimestampInRange(MONDAY_NINE_AM.toCalendar(UTC).getTimeInMillis(), UTC));
    }

    @Test
    void weeklyNowIsInRange() {
        Schedule schedule = new Schedule(null,null, false, UTC, Collections.singletonList(weeklyOneHourBeforeNowToOneHourAfter));
        assertTrue(schedule.isNowInRange());
    }

    @Test
    void weeklyIsNotInRange() {
        assertFalse(MONDAY_8AM_TO_10AM.isTimestampInRange(MONDAY_SIX_PM.toCalendar(UTC).getTimeInMillis(), UTC));
        assertFalse(MONDAY_8AM_TO_10AM.isTimestampInRange(SUNDAY_NINE_AM.toCalendar(UTC).getTimeInMillis(), UTC));
    }

    @Test
    void weeklyNowIsNotInRange() {
        Schedule schedule = new Schedule(null,null, false, UTC, Collections.singletonList(weeklyOneHourAfterNowToTwoHoursAfter));
        assertFalse(schedule.isNowInRange());
    }

    private void assertSchedule(Schedule expected, Schedule actual){
        assertNotNull(actual);

        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.isEnabled(), actual.isEnabled());
        assertEquals(expected.getTimeZone(), actual.getTimeZone());

        assertEquals(expected.getTimeRanges().size(), actual.getTimeRanges().size());
        for (int i = 0; i < expected.getSchemaVersion(); i++) {
            assertEquals(expected.getTimeRanges().get(i), actual.getTimeRanges().get(i));
        }
    }
}