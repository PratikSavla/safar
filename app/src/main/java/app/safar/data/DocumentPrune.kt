package app.safar.data

import kotlinx.coroutines.flow.first

/** Keep a file nobody points to for this long before calling it left over (a paper may be mid-save). */
private const val ORPHAN_GRACE_MS = 60 * 60 * 1000L

/** What [pruneDocuments] removed. */
data class PruneResult(val rowsWithoutFile: Int, val filesWithoutRow: Int)

/**
 * Keeps papers and their files in step. Rows whose file is gone are deleted: the database is in Android backup
 * but the files are not, so a restore or a phone-to-phone transfer brings rows without files. Files no row
 * points to (a save cut short) are deleted once older than an hour.
 */
suspend fun pruneDocuments(repo: DocumentRepository, files: DocumentFiles, nowMillis: Long): PruneResult {
    val docs = repo.documents.first()
    val missing = docs.filter { !files.exists(it.fileName) }
    missing.forEach { repo.delete(it.id) }
    val used = docs.map { it.fileName }.toSet()
    val stale = files.list().filter { it.name !in used && nowMillis - it.lastModified() > ORPHAN_GRACE_MS }
    stale.forEach { files.delete(it.name) }
    return PruneResult(missing.size, stale.size)
}
