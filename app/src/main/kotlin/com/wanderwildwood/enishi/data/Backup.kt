package com.wanderwildwood.enishi.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everyone, saved to a .vcf in a folder the reader chose, every day or every week, with the
 * last [KEEP] files kept and older ones cleared away. The file is the same one "Save everyone
 * to a file" makes, so any phone can read it back.
 *
 * Android's own job scheduler runs it, so it needs nothing of its own running and comes back
 * after a restart. It waits for the battery not to be low.
 */
class Backups(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("enishi", Context.MODE_PRIVATE)

    /** Days between backups; 0 is off. */
    var every: Int
        get() = prefs.getInt("backup_every", 0)
        set(v) {
            prefs.edit().putInt("backup_every", v).apply()
            schedule()
        }

    var folder: Uri?
        get() = prefs.getString("backup_folder", null)?.let(Uri::parse)
        set(v) {
            prefs.edit().putString("backup_folder", v?.toString()).apply()
            schedule()
        }

    /** When the last one was written, or 0. */
    val last: Long get() = prefs.getLong("backup_last", 0)

    /** Why the last try failed, or null when it did not. */
    val problem: String? get() = prefs.getString("backup_problem", null)

    /** The folder as a person would name it: its last part, "Backups" for ".../Documents/Backups". */
    fun folderName(): String? = folder?.let { uri ->
        runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?.substringAfter(':')?.trimEnd('/')?.substringAfterLast('/')
            ?.ifBlank { DocumentsContract.getTreeDocumentId(uri).substringBefore(':') }
    }

    /** Keeps the folder for good, so a backup weeks from now may still write to it. */
    fun choose(uri: Uri) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        folder?.takeIf { it != uri }?.let { old ->
            runCatching { app.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        folder = uri
    }

    fun schedule() {
        val jobs = app.getSystemService(JobScheduler::class.java) ?: return
        val days = every
        if (days <= 0 || folder == null) {
            jobs.cancel(JOB)
            return
        }
        val period = days * 24L * 60 * 60 * 1000
        val pending = jobs.getPendingJob(JOB)
        if (pending != null && pending.intervalMillis == period) return
        jobs.schedule(
            JobInfo.Builder(JOB, ComponentName(app, BackupJob::class.java))
                .setPeriodic(period)
                .setRequiresBatteryNotLow(true)
                .setPersisted(true)
                .build(),
        )
    }

    /** Writes today's file now, then clears the old ones. Returns how many people it holds. */
    fun run(): Result<Int> = runCatching {
        val tree = folder ?: error("no folder chosen")
        val resolver = app.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = "contacts-" + SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()) + ".vcf"

        val book = Book(app)
        val lookups = book.people(lastNameFirst = false, sortByLast = false).map { it.lookup }
        // Today's goes in under a new name first, and only then does an earlier one of today's go:
        // a failure half way leaves yesterday's backup, and today's old one, as they were.
        val existing = files(tree)
        // Made as plain bytes: a folder told "vcard" may add its own .vcf on the end of a name
        // that does not already end in one.
        val made = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", "$name.part")
            ?: error("the folder would not take a new file")
        try {
            resolver.openOutputStream(made, "wt")?.use { book.exportTo(lookups, it) } ?: error("the new file could not be opened")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, made) }
            throw e
        }
        existing.filter { it.second == name }.forEach { DocumentsContract.deleteDocument(resolver, it.first) }
        DocumentsContract.renameDocument(resolver, made, name)

        // The newest few stay; anything older of ours goes. Files of any other name are not ours.
        files(tree).filter { it.second.matches(OURS) }.sortedByDescending { it.second }.drop(KEEP)
            .forEach { runCatching { DocumentsContract.deleteDocument(resolver, it.first) } }
        lookups.size
    }.also { result ->
        prefs.edit().apply {
            if (result.isSuccess) {
                putLong("backup_last", System.currentTimeMillis())
                remove("backup_problem")
            } else {
                putString("backup_problem", result.exceptionOrNull()?.message ?: "it did not work")
            }
        }.apply()
    }

    /** What is in the folder: each file's address and name. */
    private fun files(tree: Uri): List<Pair<Uri, String>> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val out = mutableListOf<Pair<Uri, String>>()
        app.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                out += DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)) to c.getString(1).orEmpty()
            }
        }
        return out
    }

    companion object {
        private const val JOB = 1
        const val KEEP = 10
        private val OURS = Regex("contacts-\\d{4}-\\d{2}-\\d{2}\\.vcf")
    }
}

/** The scheduled backup, off the main thread. A failure is kept for Settings to show. */
class BackupJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            Backups(this).run()
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
