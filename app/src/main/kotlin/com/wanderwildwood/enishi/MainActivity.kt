package com.wanderwildwood.enishi

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
        val asked = Asked.of(intent)
        setContent {
            ThemeMMD(colorScheme = monochrome) {
                App(this, asked, fromElsewhere = true)
            }
        }
    }
}
