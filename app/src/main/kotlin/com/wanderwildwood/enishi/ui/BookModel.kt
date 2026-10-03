package com.wanderwildwood.enishi.ui

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wanderwildwood.enishi.data.Account
import com.wanderwildwood.enishi.data.Book
import com.wanderwildwood.enishi.data.Findable
import com.wanderwildwood.enishi.data.Group
import com.wanderwildwood.enishi.data.Person
import com.wanderwildwood.enishi.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The list, the groups and where contacts can be kept, read again whenever the store changes —
 * from here or from any other app, so an edit made in Messaging shows up without a pull.
 */
class BookModel(app: Application) : AndroidViewModel(app) {
    val book = Book(app)
    val prefs = Prefs(app)

    var people by mutableStateOf<List<Person>>(emptyList())
        private set
    var findable by mutableStateOf<Map<Long, Findable>>(emptyMap())
        private set
    var groups by mutableStateOf<List<Group>>(emptyList())
        private set
    var accounts by mutableStateOf(listOf(Account.PHONE))
        private set
    /** Until the first read lands, "no contacts yet" would be a lie. */
    var loaded by mutableStateOf(false)
        private set

    var sortByLast by mutableStateOf(prefs.sortByLast)
        private set
    var lastNameFirst by mutableStateOf(prefs.lastNameFirst)
        private set
    var saveTo by mutableStateOf(prefs.saveTo)
        private set

    private var reading: Job? = null
    private var watching = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh(soon = true)
    }

    /** Starts reading and watching; called once access has been given. */
    fun start() {
        if (!watching) {
            watching = true
            val resolver = getApplication<Application>().contentResolver
            resolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            resolver.registerContentObserver(ContactsContract.Groups.CONTENT_URI, true, observer)
        }
        refresh()
    }

    /** Re-reads everything. A burst of changes — an import — is read once, after it settles. */
    fun refresh(soon: Boolean = false) {
        reading?.cancel()
        reading = viewModelScope.launch {
            if (soon) delay(400)
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    Snapshot(
                        book.people(lastNameFirst, sortByLast),
                        book.findable(),
                        book.groups(),
                        book.accounts(),
                    )
                }.getOrNull()
            } ?: return@launch
            people = read.people
            findable = read.findable
            groups = read.groups
            accounts = read.accounts
            loaded = true
        }
    }

    private class Snapshot(val people: List<Person>, val findable: Map<Long, Findable>, val groups: List<Group>, val accounts: List<Account>)

    fun chooseSortByLast(v: Boolean) {
        prefs.sortByLast = v
        sortByLast = v
        refresh()
    }

    fun chooseLastNameFirst(v: Boolean) {
        prefs.lastNameFirst = v
        lastNameFirst = v
        refresh()
    }

    fun chooseSaveTo(v: Account) {
        prefs.saveTo = v
        saveTo = v
    }

    /** Where a new contact goes: what the reader chose, or where most already are. */
    suspend fun newContactAccount(): Account = saveTo?.takeIf { it in accounts }
        ?: withContext(Dispatchers.IO) { book.busiestAccount() }

    /** Runs store work off the main thread; a failure comes back as the exception. */
    suspend fun <T> io(block: (Book) -> T): Result<T> = withContext(Dispatchers.IO) { runCatching { block(book) } }

    override fun onCleared() {
        if (watching) getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }
}
