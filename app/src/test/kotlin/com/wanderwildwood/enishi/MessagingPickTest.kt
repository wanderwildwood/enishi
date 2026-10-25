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
}
