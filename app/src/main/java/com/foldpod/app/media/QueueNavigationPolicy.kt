package com.foldpod.app.media

/** Android-free identity and acknowledgement policy. Titles can veto reuse, never locate a row. */
data class QueueRowIdentity(
    val id: Long,
    val mediaId: String? = null,
    val mediaUri: String? = null,
    val title: String = "",
    val subtitle: String = "",
)

data class QueueSelectionRequest(
    val sessionGeneration: Long,
    val queueRevision: Long,
    val rows: List<QueueRowIdentity>,
    val index: Int,
) {
    fun matches(session: Long, revision: Long, snapshot: List<QueueRowIdentity>): Boolean =
        sessionGeneration == session && queueRevision == revision && rows == snapshot && index in snapshot.indices
}

data class QueueSelection(val sessionGeneration: Long, val rows: List<QueueRowIdentity>, val index: Int)

data class DirectQueueCapabilities(val skipToQueueItem: Boolean, val playFromUri: Boolean, val playFromMediaId: Boolean)

/** One advertised transport command; there is deliberately no relative-skip command. */
sealed class DirectQueueCommand {
    data class SkipToQueueItem(val id: Long) : DirectQueueCommand()
    data class PlayFromUri(val uri: String) : DirectQueueCommand()
    data class PlayFromMediaId(val mediaId: String) : DirectQueueCommand()
}

object DirectQueuePolicy {
    // Device-verified YouTube Music omits the action flag but handles valid queue-ID jumps.
    // The command policy below still requires unique target and current queue IDs.
    fun supportsQueueJump(advertised: Boolean, packageName: String): Boolean =
        advertised || packageName == "com.google.android.apps.youtube.music"

    fun command(rows: List<QueueRowIdentity>, targetIndex: Int, activeIndex: Int?, activeId: Long,
        capabilities: DirectQueueCapabilities): DirectQueueCommand? {
        val target = rows.getOrNull(targetIndex) ?: return null
        if (capabilities.skipToQueueItem && target.id != -1L && rows.count { it.id == target.id } == 1 &&
            activeId != -1L && activeIndex != null &&
            rows.indices.filter { rows[it].id == activeId }.singleOrNull() == activeIndex) {
            return DirectQueueCommand.SkipToQueueItem(target.id)
        }
        // Use only the session's actual description URI; a URI-shaped mediaId does not imply URI support.
        if (capabilities.playFromUri && !target.mediaUri.isNullOrBlank() &&
            rows.count { it.mediaUri == target.mediaUri } == 1) {
            return DirectQueueCommand.PlayFromUri(target.mediaUri)
        }
        if (capabilities.playFromMediaId && !target.mediaId.isNullOrBlank() &&
            rows.count { it.mediaId == target.mediaId } == 1) {
            return DirectQueueCommand.PlayFromMediaId(target.mediaId)
        }
        return null
    }

    /** A rebuilt queue may assign new IDs; preserve only an unambiguous exact content identity. */
    fun preservedIndex(request: QueueSelectionRequest, rows: List<QueueRowIdentity>): Int? {
        QueueNavigationPolicy.preservedIndex(QueueSelection(request.sessionGeneration, request.rows, request.index), rows)
            ?.let { return it }
        val target = request.rows.getOrNull(request.index) ?: return null
        for (key in listOf<(QueueRowIdentity) -> String?>({ it.mediaUri }, { it.mediaId })) {
            val value = key(target)?.takeIf { it.isNotBlank() } ?: continue
            if (request.rows.count { key(it) == value } != 1) continue
            rows.indices.filter { key(rows[it]) == value }.singleOrNull()?.let { index ->
                if (rows[index].mediaId == target.mediaId && rows[index].mediaUri == target.mediaUri) return index
            }
        }
        return null
    }

    fun metadataMatches(command: DirectQueueCommand, metadata: QueueMetadataFingerprint): Boolean = when (command) {
        is DirectQueueCommand.SkipToQueueItem -> false // Queue occurrence acknowledgement is required.
        is DirectQueueCommand.PlayFromMediaId -> metadata.mediaId == command.mediaId
        is DirectQueueCommand.PlayFromUri -> metadata.mediaUri == command.uri ||
            (command.uri.matches(Regex("spotify:track:[A-Za-z0-9]+")) && metadata.mediaId == command.uri)
    }
}

class DirectQueueNavigation(val request: QueueSelectionRequest, val command: DirectQueueCommand, activeIndex: Int?) {
    private val occurrencePlan = if (command is DirectQueueCommand.SkipToQueueItem)
        QueueNavigationPlan(request, requireNotNull(activeIndex), true) else null
    var dispatched = false
        private set

    private fun targetIndex(rows: List<QueueRowIdentity>): Int? {
        val target = request.rows.getOrNull(request.index) ?: return null
        return rows.indices.filter { rows[it].id == target.id }.singleOrNull()
            ?.takeIf { rows[it] == target }
    }

    fun refresh(rows: List<QueueRowIdentity>): Boolean =
        if (command is DirectQueueCommand.SkipToQueueItem && dispatched) targetIndex(rows) != null
        else occurrencePlan?.refresh(rows) ?: (dispatched || rows == request.rows)

    fun canDispatch(rows: List<QueueRowIdentity>, activeIndex: Int?): Boolean =
        !dispatched && (occurrencePlan?.canDispatch(rows, activeIndex) ?: (rows == request.rows))

    fun dispatch(): Boolean {
        if (dispatched) return false
        occurrencePlan?.command()
        dispatched = true
        return true
    }

    fun acknowledge(rows: List<QueueRowIdentity>, activeIndex: Int?, metadata: QueueMetadataFingerprint,
        freshMetadata: Boolean, ready: Boolean): QueueNavigationPlan.Acknowledgement {
        if (!dispatched) return QueueNavigationPlan.Acknowledgement.WAIT
        if (command is DirectQueueCommand.SkipToQueueItem) {
            val targetIndex = targetIndex(rows) ?: return QueueNavigationPlan.Acknowledgement.REJECT
            val target = rows[targetIndex]
            val metadataAgrees = (metadata.title == null || metadata.title == target.title) &&
                (metadata.artist == null || target.subtitle.isBlank() || metadata.artist == target.subtitle)
            return if (freshMetadata && ready && activeIndex == targetIndex && metadataAgrees)
                QueueNavigationPlan.Acknowledgement.COMPLETE else QueueNavigationPlan.Acknowledgement.WAIT
        }
        return occurrencePlan?.acknowledge(rows, activeIndex, freshMetadata, ready)
            ?: if (freshMetadata && ready && DirectQueuePolicy.metadataMatches(command, metadata))
                QueueNavigationPlan.Acknowledgement.COMPLETE else QueueNavigationPlan.Acknowledgement.WAIT
    }
}

data class BlockedQueueNavigation(val activeId: Long, val metadataSequence: Long)

/** A fresh, ready occurrence restores selection after a rejected command without changing players. */
object QueueNavigationRecoveryPolicy {
    fun canRecover(blocked: BlockedQueueNavigation, rows: List<QueueRowIdentity>, activeId: Long,
        metadata: QueueMetadataFingerprint, metadataSequence: Long, ready: Boolean): Boolean {
        if (!ready || metadataSequence <= blocked.metadataSequence || activeId == -1L ||
            activeId == blocked.activeId) return false
        val index = rows.indices.filter { rows[it].id == activeId }.singleOrNull() ?: return false
        if (QueueNavigationPolicy.activeIndex(rows, activeId, metadata.mediaId, metadata.mediaUri) != index) return false
        val row = rows[index]
        if (!row.mediaId.isNullOrBlank() && !metadata.mediaId.isNullOrBlank() && row.mediaId != metadata.mediaId) return false
        if (!row.mediaUri.isNullOrBlank() && !metadata.mediaUri.isNullOrBlank() && row.mediaUri != metadata.mediaUri) return false
        return (metadata.title == null || metadata.title == row.title) &&
            (metadata.artist == null || row.subtitle.isBlank() || metadata.artist == row.subtitle)
    }
}

object QueueNavigationPolicy {
    private fun unique(rows: List<QueueRowIdentity>, value: String?, key: (QueueRowIdentity) -> String?): Int? {
        if (value.isNullOrBlank()) return null
        return rows.indices.filter { key(rows[it]) == value }.singleOrNull()
    }

    fun activeIndex(rows: List<QueueRowIdentity>, activeId: Long, mediaId: String?, mediaUri: String?): Int? {
        val constraints = mutableListOf<Set<Int>>()
        if (activeId != -1L) {
            rows.indices.filter { rows[it].id == activeId }.toSet()
                .takeIf { it.isNotEmpty() }?.let { constraints += it }
        }
        for ((value, key) in listOf<Pair<String?, (QueueRowIdentity) -> String?>>(
            mediaId to { it.mediaId }, mediaUri to { it.mediaUri },
        )) {
            if (!value.isNullOrBlank()) {
                rows.indices.filter { key(rows[it]) == value }.toSet()
                    .takeIf { it.isNotEmpty() }?.let { constraints += it }
            }
        }
        if (constraints.isEmpty()) return null
        return constraints.reduce { left, right -> left.intersect(right) }.singleOrNull()
    }

    fun identifiable(rows: List<QueueRowIdentity>, index: Int): Boolean {
        val row = rows.getOrNull(index) ?: return false
        return (row.id != -1L && rows.count { it.id == row.id } == 1) ||
            unique(rows, row.mediaId) { it.mediaId } == index ||
            unique(rows, row.mediaUri) { it.mediaUri } == index
    }

    /** Preserve an occurrence for a queued request; never clamp or locate by title. */
    fun preservedIndex(previous: QueueSelection, rows: List<QueueRowIdentity>): Int? {
        val old = previous.rows.getOrNull(previous.index) ?: return null
        val index = when {
            old.id != -1L && previous.rows.count { it.id == old.id } == 1 ->
                rows.indices.filter { rows[it].id == old.id }.singleOrNull()
            unique(previous.rows, old.mediaId) { it.mediaId } == previous.index ->
                unique(rows, old.mediaId) { it.mediaId }
            unique(previous.rows, old.mediaUri) { it.mediaUri } == previous.index ->
                unique(rows, old.mediaUri) { it.mediaUri }
            else -> null
        }
        return index?.takeIf { rows[it] == old }
    }

    fun stepSelection(previous: QueueSelection, session: Long, rows: List<QueueRowIdentity>, direction: Int): QueueSelection {
        val current = selection(previous, session, rows)
        val step = direction.compareTo(0)
        val index = if (rows.isEmpty()) 0 else (current + step).coerceIn(0, rows.lastIndex)
        return QueueSelection(session, rows, index)
    }

    fun selection(previous: QueueSelection, session: Long, rows: List<QueueRowIdentity>): Int {
        if (rows.isEmpty() || previous.sessionGeneration != session) return 0
        val old = previous.rows.getOrNull(previous.index)
            ?: return previous.index.coerceIn(0, rows.lastIndex)
        val candidates = mutableListOf<Int>()
        if (old.id != -1L && previous.rows.count { it.id == old.id } == 1) {
            rows.indices.filter { rows[it].id == old.id }.singleOrNull()?.let { candidates += it }
        }
        if (unique(previous.rows, old.mediaId) { it.mediaId } == previous.index) {
            unique(rows, old.mediaId) { it.mediaId }?.let { candidates += it }
        }
        if (unique(previous.rows, old.mediaUri) { it.mediaUri } == previous.index) {
            unique(rows, old.mediaUri) { it.mediaUri }?.let { candidates += it }
        }
        val matched = candidates.distinct().singleOrNull()
        return matched?.takeIf {
            val row = rows[it]
            row.title == old.title && row.subtitle == old.subtitle &&
                row.mediaId == old.mediaId && row.mediaUri == old.mediaUri
        } ?: previous.index.coerceIn(0, rows.lastIndex)
    }
}

/** One command remains pending until its stable queue occurrence is acknowledged. */
class QueueNavigationPlan(val request: QueueSelectionRequest, initialIndex: Int, val direct: Boolean) {
    private data class Occurrence(val row: QueueRowIdentity, val key: String) {
        fun indexIn(rows: List<QueueRowIdentity>): Int? {
            val matches = rows.indices.filter {
                when (key) {
                    "id" -> rows[it].id == row.id
                    "mediaId" -> rows[it].mediaId == row.mediaId
                    else -> rows[it].mediaUri == row.mediaUri
                }
            }
            return matches.singleOrNull()?.takeIf { rows[it] == row }
        }
    }

    private fun occurrence(rows: List<QueueRowIdentity>, index: Int): Occurrence {
        val row = rows[index]
        val key = when {
            row.id != -1L && row.id !in ambiguousIds && rows.count { it.id == row.id } == 1 -> "id"
            !row.mediaId.isNullOrBlank() && rows.count { it.mediaId == row.mediaId } == 1 -> "mediaId"
            else -> "mediaUri"
        }
        return Occurrence(row, key)
    }

    var rows: List<QueueRowIdentity> = request.rows
        private set
    private val ambiguousIds = rows.filter { it.id != -1L }.groupBy { it.id }
        .filterValues { it.size > 1 }.keys.toMutableSet()
    private val target = occurrence(rows, request.index)
    private var current = occurrence(rows, initialIndex)
    private var expected: Occurrence? = null
    private val seenIds = rows.filter { it.id != -1L && it.id !in ambiguousIds }.associateBy { it.id }.toMutableMap()
    var currentIndex: Int = initialIndex
        private set
    var targetIndex: Int = request.index
        private set
    var expectedIndex: Int? = null
        private set

    /** Only prefix consumption and suffix append may remap a pending queue path. */
    fun refresh(snapshot: List<QueueRowIdentity>): Boolean {
        if (snapshot == rows) return true
        if (snapshot.isEmpty()) return false
        val removed = (0 until rows.size).firstOrNull { count ->
            val retained = rows.drop(count)
            snapshot.size >= retained.size && snapshot.take(retained.size) == retained
        } ?: return false
        val maxConsumed = if (direct && expectedIndex != null) expectedIndex!! else currentIndex + 1
        if (removed > maxConsumed || (expected == null && removed > currentIndex)) return false
        val appended = snapshot.drop(rows.size - removed)
        // An appended old ID could refer to an already-consumed occurrence or a replacement.
        if (appended.any { it.id != -1L && seenIds.containsKey(it.id) }) return false
        if (snapshot.any { row -> row.id != -1L && seenIds[row.id]?.let { it != row } == true }) return false
        val newTarget = target.indexIn(snapshot) ?: return false
        val newExpected = expected?.let { it.indexIn(snapshot) ?: return false }
        val newCurrent = current.indexIn(snapshot)
        if (newCurrent == null && expected == null) return false
        if (newExpected != null && !QueueNavigationPolicy.identifiable(snapshot, newExpected)) return false
        rows = snapshot
        targetIndex = newTarget
        currentIndex = newCurrent ?: -1
        expectedIndex = newExpected
        snapshot.filter { it.id != -1L }.groupBy { it.id }.filterValues { it.size > 1 }
            .keys.forEach { ambiguousIds += it }
        appended.filter { it.id != -1L && it.id !in ambiguousIds && snapshot.count { row -> row.id == it.id } == 1 }
            .forEach { seenIds[it.id] = it }
        return true
    }

    /** Validate the live position immediately before dispatch, including posted follow-up commands. */
    fun canDispatch(snapshot: List<QueueRowIdentity>, activeIndex: Int?): Boolean =
        expected == null && refresh(snapshot) && activeIndex != null && activeIndex == currentIndex

    fun command(): Int? {
        check(expected == null)
        check(currentIndex >= 0)
        if (currentIndex == targetIndex) return null
        val next = if (direct) targetIndex else currentIndex + if (targetIndex > currentIndex) 1 else -1
        expected = occurrence(rows, next)
        expectedIndex = next
        return next
    }

    enum class Acknowledgement { WAIT, ADVANCE, COMPLETE, REJECT }
    fun acknowledge(
        rows: List<QueueRowIdentity>, activeIndex: Int?,
        metadataConfirmed: Boolean = true, playbackReady: Boolean = true,
    ): Acknowledgement {
        if (!refresh(rows)) return Acknowledgement.REJECT
        if (!metadataConfirmed || !playbackReady) return Acknowledgement.WAIT
        if (activeIndex == null) return Acknowledgement.WAIT
        val pending = expected ?: return Acknowledgement.REJECT
        if (activeIndex == currentIndex) return Acknowledgement.WAIT
        if (activeIndex != expectedIndex) return Acknowledgement.REJECT
        current = pending
        currentIndex = activeIndex
        expected = null
        expectedIndex = null
        return if (currentIndex == targetIndex) Acknowledgement.COMPLETE else Acknowledgement.ADVANCE
    }
}

data class QueueMetadataFingerprint(
    val title: String? = null, val artist: String? = null,
    val mediaId: String? = null, val mediaUri: String? = null,
)

/** A previous command may restart the current track. Retry once only after its reset is observed. */
class PreviousRestartAcknowledgement(
    private val positionBeforeMs: Long,
    private val updateBeforeElapsedMs: Long,
    private val dispatchElapsedMs: Long,
    private val metadataBefore: QueueMetadataFingerprint = QueueMetadataFingerprint(),
) {
    private var candidateObserved = false
    private var retried = false

    fun consume(positionMs: Long, updateElapsedMs: Long, sameCurrentOccurrence: Boolean, playbackReady: Boolean): Boolean {
        if (candidateObserved || !sameCurrentOccurrence || !playbackReady || positionBeforeMs <= 3_000L ||
            positionMs !in 0L..1_000L || updateElapsedMs <= updateBeforeElapsedMs ||
            updateElapsedMs < dispatchElapsedMs) return false
        candidateObserved = true
        return true
    }

    fun confirmStableReset(positionMs: Long, sameCurrentOccurrence: Boolean,
        playbackReady: Boolean, metadata: QueueMetadataFingerprint): Boolean {
        if (!candidateObserved || retried || !sameCurrentOccurrence || !playbackReady ||
            positionMs !in 0L..1_000L || metadata != metadataBefore) return false
        retried = true
        return true
    }

    companion object {
        fun projectedPosition(positionMs: Long, updateElapsedMs: Long, speed: Float, playing: Boolean, nowElapsedMs: Long): Long {
            if (positionMs < 0L || updateElapsedMs <= 0L || updateElapsedMs > nowElapsedMs) return positionMs
            val elapsed = if (playing) nowElapsedMs - updateElapsedMs else 0L
            return (positionMs + elapsed * speed.coerceAtLeast(0f)).toLong().coerceAtLeast(0L)
        }
    }
}
