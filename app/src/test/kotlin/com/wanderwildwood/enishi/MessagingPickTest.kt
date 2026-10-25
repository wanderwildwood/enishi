package com.wanderwildwood.enishi

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mudita Messaging's "New message" asks for a contact with `_is_msg_app_request_`; the phone's
 * own Contacts app answers with its "Create Group" screen. That request must reach it.
 */
class MessagingPickTest {
    @Test fun theExtraIsTheOneMessagingSends() {
        assertEquals("_is_msg_app_request_", MessagingPick.EXTRA)
        assertEquals("com.mudita.contacts", MessagingPick.STOCK_PACKAGE)
    }

    @Test fun messagingsNewMessagePickGoesToTheStockScreen() {
        assertTrue(MessagingPick.handOver(Intent.ACTION_PICK, flagged = true, stockPresent = true))
        assertTrue(MessagingPick.handOver(Intent.ACTION_GET_CONTENT, flagged = true, stockPresent = true))
    }

    @Test fun anotherAppsPlainPickIsAnsweredHere() {
        assertFalse(MessagingPick.handOver(Intent.ACTION_PICK, flagged = false, stockPresent = true))
    }

    @Test fun withoutTheStockAppItIsAnsweredHere() {
        assertFalse(MessagingPick.handOver(Intent.ACTION_PICK, flagged = true, stockPresent = false))
    }

    @Test fun onlyPicksAreHandedOver() {
        assertFalse(MessagingPick.handOver(Intent.ACTION_VIEW, flagged = true, stockPresent = true))
        assertFalse(MessagingPick.handOver(Intent.ACTION_INSERT, flagged = true, stockPresent = true))
        assertFalse(MessagingPick.handOver(null, flagged = true, stockPresent = true))
    }

    @Test fun theStockScreenIsUsableOnlyWhenEverythingAboutItIsOn() {
        assertTrue(MessagingPick.usable(exported = true, componentEnabled = true, appEnabled = true))
        // The app a replacement switches off.
        assertFalse(MessagingPick.usable(exported = true, componentEnabled = true, appEnabled = false))
        assertFalse(MessagingPick.usable(exported = true, componentEnabled = false, appEnabled = true))
        assertFalse(MessagingPick.usable(exported = false, componentEnabled = true, appEnabled = true))
    }

    @Test fun whichPath() {
        fun path(action: String?, flagged: Boolean, exported: Boolean, comp: Boolean, app: Boolean): String {
            val usable = MessagingPick.usable(exported, comp, app)
            return when {
                MessagingPick.handOver(action, flagged, usable) -> "stock"
                MessagingPick.answeredHere(action, flagged) -> "own-messaging"
                else -> "own-plain"
            }
        }
        val pick = Intent.ACTION_PICK
        assertEquals("stock", path(pick, true, true, true, true))
        assertEquals("own-messaging", path(pick, true, true, true, false))
        assertEquals("own-messaging", path(pick, true, false, true, true))
        assertEquals("own-messaging", path(Intent.ACTION_GET_CONTENT, true, true, false, true))
        assertEquals("own-plain", path(pick, false, true, true, true))
        assertEquals("own-plain", path(pick, false, true, true, false))
        assertEquals("own-plain", path(Intent.ACTION_VIEW, true, true, true, false))
    }

    @Test fun theResultExtrasAreTheOnesMessagingReads() {
        assertEquals("contact_picker_selected_number", MessagingPick.RESULT_NUMBER)
        assertEquals("_has_plain_number_", MessagingPick.RESULT_HAS_PLAIN)
        assertEquals("_plain_number_", MessagingPick.RESULT_PLAIN)
        assertEquals("com.mudita.messages", MessagingPick.MESSAGES_PACKAGE)
    }
}
