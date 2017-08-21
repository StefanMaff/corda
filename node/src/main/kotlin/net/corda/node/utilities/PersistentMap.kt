package net.corda.node.utilities


import com.google.common.cache.RemovalCause
import com.google.common.cache.RemovalListener
import com.google.common.cache.RemovalNotification
import net.corda.core.utilities.loggerFor
import java.util.*


/**
 * Implements an unbound caching layer on top of a table accessed via Hibernate mapping.
 */
class PersistentMap<K, V, E, EK> (
        val toPersistentEntityKey: (K) -> EK,
        val fromPersistentEntity: (E) -> Pair<K,V>,
        val toPersistentEntity: (key: K, value: V) -> E,
        val persistentEntityClass: Class<E>
) : MutableMap<K, V>, AbstractMap<K, V>() {

    private companion object {
        val log = loggerFor<PersistentMap<*, *, *, *>>()
    }

    private val cache = NonInvalidatingUnboundCache(
            concurrencyLevel = 8,
            loadFunction = { key -> Optional.ofNullable(loadValue(key)) },
            removalListener = ExplicitRemoval(toPersistentEntityKey, persistentEntityClass)
    )

    class ExplicitRemoval<K, V, E, EK>(val toPersistentEntityKey: (K) -> EK, val persistentEntityClass: Class<E>): RemovalListener<K,V> {
        override fun onRemoval(notification: RemovalNotification<K, V>?) {
            when (notification?.cause) {
                RemovalCause.EXPLICIT -> {
                    val session = DatabaseTransactionManager.current().session
                    val elem = session.find(persistentEntityClass, toPersistentEntityKey(notification.key))
                    if (elem != null) {
                        session.remove(elem)
                    }
                }
                RemovalCause.EXPIRED, RemovalCause.SIZE, RemovalCause.COLLECTED -> {
                    log.error("Entry was removed from cache!!!")
                }
                //else do nothing for RemovalCause.REPLACED
            }
        }
    }

    override operator fun get(key: K): V? {
        return cache.get(key).orElse(null)
    }

    fun all(): Sequence<Pair<K, V>> {
        return cache.asMap().map { entry -> Pair(entry.key as K, entry.value as V) }.asSequence()
    }


    override val size = all().count()

    private tailrec fun set(key: K, value: V, logWarning: Boolean = true, store: (K,V) -> V?): Boolean {
        var insertionAttempt = false
        var isUnique = true
        val existingInCache = cache.get(key) { // Thread safe, if multiple threads may wait until the first one has loaded.
            insertionAttempt = true
            // Key wasn't in the cache and might be in the underlying storage.
            // Depending on 'store' method, this may insert without checking key duplication or it may avoid inserting a duplicated key.
            val existingInDb = store(key, value)
            if (existingInDb != null) { // Always reuse an existing value from the storage of a duplicated key.
                Optional.of(existingInDb)
            } else {
                Optional.of(value)
            }
        }
        if (!insertionAttempt) {
            if (existingInCache.isPresent) {
                // Key already exists in cache, do nothing.
                isUnique = false
            } else {
                // This happens when the key was queried before with no value associated. We invalidate the cached null
                // value and recursively call set again. This is to avoid race conditions where another thread queries after
                // the invalidate but before the set.
                cache.invalidate(key)
                return set(key, value, logWarning, store)
            }
        }
        if (logWarning && !isUnique) {
            log.warn("Double insert in ${this.javaClass.name} for entity class $persistentEntityClass key $key, not inserting the second time")
        }
        return isUnique
    }

    /**
     * Associates the specified value with the specified key in this map and persists it.
     * If the map previously contained a mapping for the key, the behaviour is unpredictable and may throw an error from the underlying storage.
     */
    operator fun set(key: K, value: V) =
            set(key, value, logWarning = false) {
                key,value -> DatabaseTransactionManager.current().session.save(toPersistentEntity(key,value))
                null
            }

    /**
     * Associates the specified value with the specified key in this map and persists it.
     * If the map previously contained a mapping for the key, the old value is not replaced.
     * @return true if added key was unique, otherwise false
     */
    fun addWithDuplicatesAllowed(key: K, value: V): Boolean =
            set(key, value) {
                key, value ->
                val existingEntry = DatabaseTransactionManager.current().session.find(persistentEntityClass, toPersistentEntityKey(key))
                if (existingEntry == null) {
                    DatabaseTransactionManager.current().session.save(toPersistentEntity(key,value))
                    null
                } else {
                    fromPersistentEntity(existingEntry).second
                }
            }

    private fun loadValue(key: K): V? {
        val result = DatabaseTransactionManager.current().session.find(persistentEntityClass, toPersistentEntityKey(key))
        return result?.let(fromPersistentEntity)?.second
    }

    /**
     * Removes the mapping for the specified key from this map and underlying storage if present.
     */
    override fun remove(key: K): V? {
        val result = cache.get(key).orElse(null)
        cache.invalidate(key)
        return result
    }

    private class NotReallyMutableEntry<K, V>(key: K, value: V) : AbstractMap.SimpleImmutableEntry<K, V>(key, value), MutableMap.MutableEntry<K, V> {
        override fun setValue(newValue: V): V {
            throw UnsupportedOperationException("Not really mutable.  Implement if really required.")
        }
    }

    private inner class EntryIterator : MutableIterator<MutableMap.MutableEntry<K, V>> {
        private val iterator = all().map { NotReallyMutableEntry(it.first, it.second) }.iterator()

        private var current: MutableMap.MutableEntry<K, V>? = null

        override fun hasNext(): Boolean = iterator.hasNext()

        override fun next(): MutableMap.MutableEntry<K, V> {
            val extractedNext = iterator.next()
            current = extractedNext
            return extractedNext
        }

        override fun remove() {
            val savedCurrent = current ?: throw IllegalStateException("Not called next() yet or already removed.")
            current = null
            remove(savedCurrent.key)
        }
    }

    override val keys: MutableSet<K> get() {
        return object : AbstractSet<K>() {
            override val size: Int get() = this@PersistentMap.size
            override fun iterator(): MutableIterator<K> {
                return object : MutableIterator<K> {
                    private val entryIterator = EntryIterator()

                    override fun hasNext(): Boolean = entryIterator.hasNext()
                    override fun next(): K = entryIterator.next().key
                    override fun remove() {
                        entryIterator.remove()
                    }
                }
            }
        }
    }

    override val values: MutableCollection<V> get() {
        return object : AbstractCollection<V>() {
            override val size: Int get() = this@PersistentMap.size
            override fun iterator(): MutableIterator<V> {
                return object : MutableIterator<V> {
                    private val entryIterator = EntryIterator()

                    override fun hasNext(): Boolean = entryIterator.hasNext()
                    override fun next(): V = entryIterator.next().value
                    override fun remove() {
                        entryIterator.remove()
                    }
                }
            }
        }
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() {
        return object : AbstractSet<MutableMap.MutableEntry<K, V>>() {
            override val size: Int get() = this@PersistentMap.size
            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
                return object : MutableIterator<MutableMap.MutableEntry<K, V>> {
                    private val entryIterator = EntryIterator()

                    override fun hasNext(): Boolean = entryIterator.hasNext()
                    override fun next(): MutableMap.MutableEntry<K, V> = entryIterator.next()
                    override fun remove() {
                        entryIterator.remove()
                    }
                }
            }
        }
    }

    override fun put(key: K, value: V): V? {
        val old = cache.get(key)
        addWithDuplicatesAllowed(key, value)
        return old.orElse(null)
    }
}
