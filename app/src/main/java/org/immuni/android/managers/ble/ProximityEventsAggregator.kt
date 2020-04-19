package org.immuni.android.managers.ble

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.immuni.android.db.ImmuniDatabase
import org.immuni.android.models.ProximityEvent
import org.immuni.android.util.log
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*
import kotlin.concurrent.timer
import kotlin.math.pow
import kotlin.math.roundToInt

class ProximityEventsAggregator(
    val database: ImmuniDatabase,
    TIME_WINDOW: Long,
    val scope: CoroutineScope
    ): KoinComponent {

    private val mutex = Mutex()
    private val timerJob: Job

    init {
        timerJob = scope.launch {
            repeat(Int.MAX_VALUE) {
                delay(TIME_WINDOW)
                tick()
            }
        }
    }

    private val proximityEvents = mutableListOf<ProximityEvent>()

    fun stop() {
        timerJob.cancel()
    }

    fun addProximityEvents(events: List<ProximityEvent>) = runBlocking {
        log("Raw scan: ${events.map { "${it.btId} - ${it.rssi}" }.joinToString(", ")}")
        mutex.withLock {
            proximityEvents.addAll(events)
        }
    }

    private fun tick() = runBlocking{
        if (proximityEvents.isEmpty()) return@runBlocking
        mutex.withLock {
            store(aggregate())
            clear()
        }

    }

    private fun aggregate(): Collection<ProximityEvent> {
        // if in the same scan result we have the same ids, compute the average rssi
        val rssisGroupedById = proximityEvents.groupingBy { it.btId }
        val averagedRssisGroupedById =
            rssisGroupedById.fold(RssiRollingAverage()) { rollingAverage, contact ->
                rollingAverage.newAverage(contact)
            }

        val averagedRssiContactsGroupedById = averagedRssisGroupedById.mapValues {
            it.value.contact
        }

        averagedRssiContactsGroupedById.values.forEach {
            log("Aggregate scan: ${it.btId} - ${it.rssi} - distance: ${distance(it.rssi, it.txPower)} meters")
        }

        return averagedRssiContactsGroupedById.values
    }

    private fun clear() {
        proximityEvents.clear()
    }

    private fun store(events: Collection<ProximityEvent>) {
        scope.launch {
            events.forEach {
                database.addContact(
                    btId = it.btId,
                    txPower = it.txPower,
                    rssi = it.rssi,
                    date = it.date
                )
            }
            database.rawDao().checkpoint()
        }
    }

    fun distance(rssi: Int, txPower: Int): Float {
        val p0 = -89f
        val gamma = 2f
        return 10.0.pow((p0 - rssi) / (10.0 * gamma)).toFloat()
    }
}

internal data class RssiRollingAverage(
    val countSoFar: Int = 0,
    val averageRssi: Double = 0.0
) {
    lateinit var contact: ProximityEvent

    fun newAverage(newContact: ProximityEvent): RssiRollingAverage {
        val newAverage = RssiRollingAverage(
            countSoFar + 1,
            (averageRssi * countSoFar + newContact.rssi) / (countSoFar + 1).toDouble()
        )
        newAverage.contact = newContact.copy(rssi = newAverage.averageRssi.roundToInt())
        return newAverage
    }
}