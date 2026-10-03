package com.wanderwildwood.enishi.ui

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.text.format.DateFormat
import com.wanderwildwood.enishi.R
import com.wanderwildwood.enishi.data.Account
import com.wanderwildwood.enishi.data.Day
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * The words for kinds of number and address are Android's own, in the phone's language, so a
 * "Work" written by any other app reads the same here and a custom label is shown as written.
 */
object Labels {
    val PHONE_TYPES = listOf(Phone.TYPE_MOBILE, Phone.TYPE_HOME, Phone.TYPE_WORK, Phone.TYPE_MAIN, Phone.TYPE_OTHER)
    val EMAIL_TYPES = listOf(Email.TYPE_HOME, Email.TYPE_WORK, Email.TYPE_OTHER)
    val ADDRESS_TYPES = listOf(StructuredPostal.TYPE_HOME, StructuredPostal.TYPE_WORK, StructuredPostal.TYPE_OTHER)
    val EVENT_TYPES = listOf(Event.TYPE_BIRTHDAY, Event.TYPE_ANNIVERSARY, Event.TYPE_OTHER)

    fun phone(context: Context, type: Int, label: String?): String =
        Phone.getTypeLabel(context.resources, type, label).toString()

    fun email(context: Context, type: Int, label: String?): String =
        Email.getTypeLabel(context.resources, type, label).toString()

    fun address(context: Context, type: Int, label: String?): String =
        StructuredPostal.getTypeLabel(context.resources, type, label).toString()

    fun event(context: Context, type: Int, label: String?): String =
        context.getString(Event.getTypeResource(type)).let { if (type == Event.TYPE_CUSTOM && !label.isNullOrBlank()) label else it }

    fun account(context: Context, account: Account): String =
        if (account.isPhone) context.getString(R.string.account_phone) else account.name.orEmpty()

    /** "14 May 1990", or "14 May" when the year is not known — in the phone's own order. */
    fun day(stored: String): String {
        val d = Day.parse(stored) ?: return stored
        val cal = Calendar.getInstance().apply { clear(); set(d.year ?: 2000, d.month - 1, d.day) }
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), if (d.year != null) "dMMMMy" else "dMMMM")
        return SimpleDateFormat(pattern, Locale.getDefault()).format(cal.time)
    }

    /** The next type in the list, so a press on a type word steps it on. */
    fun next(types: List<Int>, current: Int): Int {
        val i = types.indexOf(current)
        return types[(i + 1) % types.size]
    }
}
