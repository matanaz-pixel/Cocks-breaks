package il.gallerydoctor.core

/**
 * Hands out isolated worker processes (indexes into the manifest's list of `:scannerN` services) so that
 * no process ever crashes twice within a minute.
 *
 * Why: Android marks a process that crashes twice within ~60s as "bad" and then refuses to start it
 * again (bindService returns false: "process is bad"). A file that crashes a decoder deterministically
 * would otherwise crash the same process again on its retry, and a few such files would lock out every
 * worker and abort the scan. Rotating over many process names, and resting a crashed one for
 * [safeGapMs], avoids that.
 */
class ProcessPool(
    private val size: Int,
    private val clock: () -> Long,
    private val safeGapMs: Long = 65_000,
) {
    private val lastCrash = LongArray(size) { NEVER }
    private val inUse = BooleanArray(size)
    private val dead = BooleanArray(size)

    /** A free process that has not crashed recently (never-crashed first, then the one that crashed longest ago), or -1. */
    @Synchronized
    fun tryAcquire(): Int {
        val now = clock()
        var best = -1
        for (i in 0 until size) {
            if (inUse[i] || dead[i]) continue
            if (lastCrash[i] != NEVER && now - lastCrash[i] < safeGapMs) continue
            if (best == -1 || lastCrash[i] < lastCrash[best]) best = i
        }
        if (best >= 0) inUse[best] = true
        return best
    }

    /** How long until some process becomes usable; null when none ever will (all refused by the system). */
    @Synchronized
    fun msUntilAvailable(): Long? {
        val now = clock()
        var wait: Long? = null
        for (i in 0 until size) {
            if (dead[i]) continue
            val w = when {
                inUse[i] -> 200L // somebody holds it; it will be released soon
                lastCrash[i] == NEVER -> 0L
                else -> maxOf(0L, lastCrash[i] + safeGapMs - now)
            }
            wait = if (wait == null) w else minOf(wait, w)
        }
        return wait
    }

    @Synchronized
    fun release(index: Int, crashed: Boolean) {
        inUse[index] = false
        if (crashed) lastCrash[index] = clock()
    }

    /** The system refused to bind this process: never use it again in this scan. */
    @Synchronized
    fun markDead(index: Int) {
        inUse[index] = false
        dead[index] = true
    }

    @Synchronized
    fun usableCount(): Int = (0 until size).count { !dead[it] }

    private companion object {
        const val NEVER = Long.MIN_VALUE
    }
}
