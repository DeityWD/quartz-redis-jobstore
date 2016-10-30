package net.joelinn.quartz;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.quartz.Calendar;
import org.quartz.JobDetail;
import org.quartz.JobPersistenceException;
import org.quartz.impl.calendar.HolidayCalendar;
import org.quartz.impl.triggers.CronTriggerImpl;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.collection.IsMapContaining.hasKey;
import static org.junit.Assert.*;

/**
 * Joe Linn
 * 7/17/2014
 */
public class StoreCalendarTest extends BaseTest{
    @Test
    public void storeCalendar() throws Exception {
        final String calendarName = "weekdayCalendar";
        Calendar calendar = getCalendar();

        jobStore.storeCalendar(calendarName, calendar, false, false);

        final String calendarHashKey = schema.calendarHashKey(calendarName);
        Map<String, String> calendarMap = jedis.hgetAll(calendarHashKey);

        assertThat(calendarMap, hasKey("calendar_class"));
        assertEquals(calendar.getClass().getName(), calendarMap.get("calendar_class"));
        assertThat(calendarMap, hasKey("calendar_json"));

        ObjectMapper mapper = new ObjectMapper();

        Map<String, Object> calendarJson = mapper.readValue(calendarMap.get("calendar_json"), new TypeReference<HashMap<String, Object>>() {
        });
        assertThat(calendarJson, hasKey("description"));
        assertEquals("Only run on weekdays.", calendarJson.get("description"));
    }

    @Test
    public void storeCalendarWithReplace() throws Exception {
        final String calendarName = "weekdayCalendar";
        Calendar calendar = getCalendar();
        jobStore.storeCalendar(calendarName, calendar, true, false);
        jobStore.storeCalendar(calendarName, calendar, true, false);
    }

    @Test(expected = JobPersistenceException.class)
    public void storeCalendarNoReplace() throws Exception {
        final String calendarName = "weekdayCalendar";
        Calendar calendar = getCalendar();
        jobStore.storeCalendar(calendarName, calendar, false, false);
        jobStore.storeCalendar(calendarName, calendar, false, false);
    }

    @Test
    public void retrieveCalendar() throws Exception {
        final String calendarName = "weekdayCalendar";
        Calendar calendar = getCalendar();
        jobStore.storeCalendar(calendarName, calendar, false, false);

        Calendar retrievedCalendar = jobStore.retrieveCalendar(calendarName);

        assertEquals(calendar.getClass(), retrievedCalendar.getClass());
        assertEquals(calendar.getDescription(), retrievedCalendar.getDescription());
        long currentTime = System.currentTimeMillis();
        assertEquals(calendar.getNextIncludedTime(currentTime), retrievedCalendar.getNextIncludedTime(currentTime));
    }

    @Test
    public void getNumberOfCalendars() throws Exception {
        jobStore.storeCalendar("calendar1", getCalendar(), false, false);
        jobStore.storeCalendar("calendar1", getCalendar(), true, false);
        jobStore.storeCalendar("calendar2", getCalendar(), false, false);

        int numberOfCalendars = jobStore.getNumberOfCalendars();

        assertEquals(2, numberOfCalendars);
    }

    @Test
    public void getCalendarNames() throws Exception {
        List<String> calendarNames = jobStore.getCalendarNames();

        assertThat(calendarNames, not(nullValue()));
        assertThat(calendarNames, hasSize(0));

        jobStore.storeCalendar("calendar1", getCalendar(), false, false);
        jobStore.storeCalendar("calendar2", getCalendar(), false, false);

        calendarNames = jobStore.getCalendarNames();

        assertThat(calendarNames, hasSize(2));
        assertThat(calendarNames, containsInAnyOrder("calendar2", "calendar1"));
    }

    @Test
    public void removeCalendar() throws Exception {
        assertFalse(jobStore.removeCalendar("foo"));

        jobStore.storeCalendar("calendar1", getCalendar(), false, false);

        assertTrue(jobStore.removeCalendar("calendar1"));

        assertThat(jobStore.retrieveCalendar("calendar1"), nullValue());
    }

    @Test(expected = JobPersistenceException.class)
    public void removeCalendarWithTrigger() throws Exception {
        // store trigger and job
        JobDetail job = getJobDetail();
        jobStore.storeJob(job, false);
        CronTriggerImpl trigger1 = getCronTrigger("trigger1", "group1", job.getKey());
        jobStore.storeTrigger(trigger1, false);

        jobStore.removeCalendar(trigger1.getCalendarName());
    }

    @Test
    public void holidayCalendar() throws Exception {
        // HolidayCalendar sets the time of any given Date to 00:00:00
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        final Date excludedDate = cal.getTime();

        HolidayCalendar calendar = new HolidayCalendar();
        calendar.addExcludedDate(excludedDate);
        final String name = "holidayCalendar";
        jobStore.storeCalendar(name, calendar, true, true);

        final String calendarHashKey = schema.calendarHashKey(name);
        Map<String, String> calendarMap = jedis.hgetAll(calendarHashKey);

        assertThat(calendarMap, hasKey("calendar_class"));
        assertThat(calendarMap.get("calendar_class"), equalTo(HolidayCalendar.class.getName()));
        assertThat(calendarMap, hasKey("calendar_json"));
        String json = calendarMap.get("calendar_json");
        assertThat(json, containsString("\"dates\":["));
        assertThat(json, not(containsString("\"excludedDates\":")));

        Calendar retrieved = jobStore.retrieveCalendar(name);
        assertThat(retrieved, notNullValue());
        assertThat(retrieved, instanceOf(HolidayCalendar.class));
        HolidayCalendar retrievedHoliday = (HolidayCalendar) retrieved;
        assertThat(retrievedHoliday.getExcludedDates(), hasItem(excludedDate));
    }
}
