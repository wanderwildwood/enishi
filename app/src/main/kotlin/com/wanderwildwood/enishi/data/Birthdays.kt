package com.wanderwildwood.enishi.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import com.wanderwildwood.enishi.R
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Birthdays put in the phone's calendar, one person at a time, when the switch beside their
 * birthday is on. They go in a calendar of this app's own, kept on the phone and called
 * "Birthdays", so any calendar app shows them and its reminders ring for them, and nothing is
 * written into a calendar that syncs somewhere else. It is read-only to calendar apps: the
 * birthday is changed here, and the calendar follows.
 *
 * Each event remembers its person by lookup key and id, the pair Android resolves even after
 * the contact is joined or split. That is the only record of which switches are on: there is
 * no list kept anywhere else.
 */
class Birthdays(private val context: Context) {
    private val resolver = context.contentResolver

    fun allowed(): Boolean = PERMISSIONS.all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    /** Whether this person's birthday is in the calendar. */
    fun isOn(contactId: Long): Boolean = allowed() && events().any { it.contactId == contactId }

    /** Puts this person's birthday in the calendar, or brings what is there up to date. */
    fun add(contactId: Long) {
        if (!allowed()) return
        val day = birthday(contactId) ?: return remove(contactId)
        val name = name(contactId) ?: return
        val lookup = lookup(contactId) ?: return
        val calendar = calendarId(create = true) ?: return
        val values = eventValues(calendar, contactId, lookup, name, day)
        val have = events().filter { it.contactId == contactId }
        if (have.isEmpty()) {
            val id = resolver.insert(asCalendar(Events.CONTENT_URI), values)?.let(ContentUris::parseId) ?: return
            resolver.insert(
                asCalendar(Reminders.CONTENT_URI),
                ContentValues().apply {
                    put(Reminders.EVENT_ID, id)
                    put(Reminders.MINUTES, REMIND_AT)
                    put(Reminders.METHOD, Reminders.METHOD_ALERT)
                },
            )
        } else {
            resolver.update(asCalendar(ContentUris.withAppendedId(Events.CONTENT_URI, have.first().id)), values, null, null)
            have.drop(1).forEach { delete(it.id) }
        }
    }

    fun remove(contactId: Long) {
        if (!allowed()) return
        events().filter { it.contactId == contactId }.forEach { delete(it.id) }
    }

    /**
     * Brings every birthday in the calendar into line with the contacts: a new name or day is
     * written in, and a person gone, or no longer with a birthday, is taken out. Run whenever the
     * contacts change, by this app or any other.
     */
    fun follow() {
        if (!allowed()) return
        val calendar = calendarId(create = false) ?: return
        val seen = mutableSetOf<Long>()
        for (e in events()) {
            val id = e.contactId
            // Two people merged into one bring two events; the one is kept.
            if (id != null && !seen.add(id)) {
                delete(e.id)
                continue
            }
            val day = id?.let(::birthday)
            val name = id?.let(::name)
            val lookup = id?.let(::lookup)
            if (id == null || day == null || name == null || lookup == null) {
                delete(e.id)
                continue
            }
            val values = eventValues(calendar, id, lookup, name, day)
            if (values.getAsString(Events.TITLE) != e.title || values.getAsLong(Events.DTSTART) != e.start ||
                values.getAsString(Events.RRULE) != e.rule || lookup != e.lookup || id != e.storedId
            ) {
                resolver.update(asCalendar(ContentUris.withAppendedId(Events.CONTENT_URI, e.id)), values, null, null)
            }
        }
    }

    // ---------------------------------------------------------------- the calendar

    private class Held(val id: Long, val lookup: String?, val storedId: Long?, val contactId: Long?, val title: String?, val start: Long, val rule: String?)

    private fun events(): List<Held> {
        val calendar = calendarId(create = false) ?: return emptyList()
        val out = mutableListOf<Held>()
        resolver.query(
            Events.CONTENT_URI,
            arrayOf(Events._ID, Events.SYNC_DATA1, Events.SYNC_DATA2, Events.TITLE, Events.DTSTART, Events.RRULE),
            "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0",
            arrayOf(calendar.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val lookup = c.getString(1)
                val stored = c.getString(2)?.toLongOrNull()
                out += Held(c.getLong(0), lookup, stored, resolve(lookup, stored), c.getString(3), c.getLong(4), c.getString(5))
            }
        }
        return out
    }

    /** The contact an event was made for, as it is now: joined, split or gone. */
    private fun resolve(lookup: String?, id: Long?): Long? {
        if (lookup.isNullOrEmpty() || id == null) return null
        val uri = Contacts.getLookupUri(id, lookup) ?: return null
        return runCatching { Contacts.lookupContact(resolver, uri)?.let(ContentUris::parseId) }.getOrNull()
    }

    private fun calendarId(create: Boolean): Long? {
        resolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID),
            "${Calendars.ACCOUNT_TYPE} = ? AND ${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.NAME} = ?",
            arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL, ACCOUNT, CALENDAR),
            null,
        )?.use { if (it.moveToFirst()) return it.getLong(0) }
        if (!create) return null
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT)
            put(Calendars.NAME, CALENDAR)
            put(Calendars.CALENDAR_DISPLAY_NAME, context.getString(R.string.birthdays_calendar))
            put(Calendars.CALENDAR_COLOR, 0xFF000000.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ)
            put(Calendars.CALENDAR_TIME_ZONE, "UTC")
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
        }
        return resolver.insert(asCalendar(Calendars.CONTENT_URI), values)?.let(ContentUris::parseId)
    }

    /**
     * Written as the calendar's own keeper: a local calendar has no sync adapter, so a delete
     * made any other way would wait for one forever, and a read-only calendar takes writes from
     * no one else.
     */
    private fun asCalendar(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private fun delete(eventId: Long) {
        resolver.delete(asCalendar(ContentUris.withAppendedId(Events.CONTENT_URI, eventId)), null, null)
    }

    private fun eventValues(calendar: Long, contactId: Long, lookup: String, name: String, day: Day): ContentValues {
        val (start, rule) = yearly(day, LocalDate.now().year)
        return ContentValues().apply {
            put(Events.CALENDAR_ID, calendar)
            put(Events.TITLE, context.getString(R.string.birthdays_title, name))
            put(Events.DTSTART, start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            put(Events.DURATION, "P1D")
            put(Events.ALL_DAY, 1)
            put(Events.EVENT_TIMEZONE, "UTC")
            put(Events.RRULE, rule)
            put(Events.AVAILABILITY, Events.AVAILABILITY_FREE)
            put(Events.HAS_ALARM, 1)
            put(Events.SYNC_DATA1, lookup)
            put(Events.SYNC_DATA2, contactId.toString())
        }
    }

    // ---------------------------------------------------------------- the contact

    private fun birthday(contactId: Long): Day? = resolver.query(
        Data.CONTENT_URI,
        arrayOf(Event.START_DATE),
        "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ? AND ${Event.TYPE} = ?",
        arrayOf(contactId.toString(), Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString()),
        null,
    )?.use { c ->
        generateSequence { if (c.moveToNext()) c.getString(0) else null }.firstNotNullOfOrNull { it?.let(Day::parse) }
    }

    private fun name(contactId: Long): String? = one(contactId, Contacts.DISPLAY_NAME)?.takeIf { it.isNotBlank() }

    private fun lookup(contactId: Long): String? = one(contactId, Contacts.LOOKUP_KEY)

    private fun one(contactId: Long, column: String): String? = resolver.query(
        ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId), arrayOf(column), null, null, null,
    )?.use { if (it.moveToFirst()) it.getString(0) else null }

    companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        private const val ACCOUNT = "Contacts"
        private const val CALENDAR = "enishi_birthdays"

        /** On the day, at nine in the morning: minutes before the midnight that starts it. */
        const val REMIND_AT = -540

        /**
         * Where the yearly event starts, and how it repeats. A known year starts it on the day
         * itself; an unknown one starts it this year. The 29th of February falls on the last
         * day of February, so it is not missed three years in four.
         */
        fun yearly(day: Day, thisYear: Int): Pair<LocalDate, String> {
            val year = day.year ?: thisYear
            if (day.month == 2 && day.day == 29) {
                val first = LocalDate.of(year, 3, 1).minusDays(1)
                return first to "FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1"
            }
            return LocalDate.of(year, day.month, day.day) to "FREQ=YEARLY"
        }
    }
}
