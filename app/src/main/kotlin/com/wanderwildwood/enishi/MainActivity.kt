package com.wanderwildwood.enishi

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.enishi.ui.monochrome

/** The address book, from the launcher. One of it at a time. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThemeMMD(colorScheme = monochrome) {
                App(this, Asked.Browse, fromElsewhere = false)
            }
        }
    }
}

/**
 * Every request another app makes of a contacts app: show this person, add this number, pick
 * an address, read this card. Standard launch mode, not the address book's single one, so it
 * opens over the app that asked and the answer goes back to it — Back from a contact Messaging
 * showed lands in the conversation, not in the address book.
 */
class AskActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (handedToStockPicker()) {
            finish()
            return
        }
        val asked = Asked.of(intent)
        setContent {
            ThemeMMD(colorScheme = monochrome) {
                App(this, asked, fromElsewhere = true)
            }
        }
    }

    /**
     * Messaging's new-message request, sent on to the phone's own Contacts app (see
     * [MessagingPick]). The result is forwarded, so it reaches Messaging as if this app had
     * never been asked. Anything that goes wrong leaves the request to be answered here.
     */
    private fun handedToStockPicker(): Boolean {
        val stock = ComponentName(MessagingPick.STOCK_PACKAGE, MessagingPick.STOCK_PICKER)
        val present = try {
            val info = packageManager.getActivityInfo(stock, 0)
            info.exported && info.isEnabled
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        if (!MessagingPick.handOver(intent.action, intent.getBooleanExtra(MessagingPick.EXTRA, false), present)) return false
        val onward = Intent(intent).setComponent(stock)
        if (callingActivity != null) onward.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
        return try {
            startActivity(onward)
            true
        } catch (_: RuntimeException) {
            false
        }
    }
}
