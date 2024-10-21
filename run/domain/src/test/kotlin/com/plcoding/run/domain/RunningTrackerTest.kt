@file:OptIn(ExperimentalCoroutinesApi::class)

package com.plcoding.run.domain

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import com.plcoding.core.connectivity.domain.messaging.MessagingAction
import com.plcoding.core.domain.location.Location
import com.plcoding.core.domain.location.LocationWithAltitude
import com.plcoding.test.LocationObserverFake
import com.plcoding.test.MainCoroutineExtension
import com.plcoding.test.PhoneToWatchConnectorFake
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.math.roundToInt

class RunningTrackerTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainCoroutineExtension = MainCoroutineExtension(StandardTestDispatcher())
    }

    private lateinit var runningTracker: RunningTracker
    private lateinit var locationObserverFake: LocationObserverFake
    private lateinit var watchConnectorFake: PhoneToWatchConnectorFake
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: CoroutineScope

    @BeforeEach
    fun setUp() {
        locationObserverFake = LocationObserverFake()
        watchConnectorFake = PhoneToWatchConnectorFake()
        testDispatcher = mainCoroutineExtension.testDispatcher
        testScope = CoroutineScope(testDispatcher)

        runningTracker = RunningTracker(
            locationObserver = locationObserverFake,
            applicationScope = testScope,
            watchConnector = watchConnectorFake
        )
    }

    @Test
    fun `combining run data`() = runTest {
        runningTracker.runData.test {
            skipItems(1)

            runningTracker.startObservingLocation()
            runningTracker.setIsTracking(true)

            testDispatcher.scheduler.runCurrent()

            val location1 = LocationWithAltitude(
                location = Location(
                    lat = 1.0,
                    long = 1.0
                ),
                altitude = 1.0
            )
            locationObserverFake.trackLocation(location1)
            val emission1 = awaitItem()

            val location2 = LocationWithAltitude(
                location = Location(
                    lat = 2.0,
                    long = 2.0
                ),
                altitude = 2.0
            )
            locationObserverFake.trackLocation(location2)
            val emission2 = awaitItem()

            watchConnectorFake.sendFromWatchToPhone(
                MessagingAction.HeartRateUpdate(160)
            )
            val emission3 = awaitItem()

            watchConnectorFake.sendFromWatchToPhone(
                MessagingAction.HeartRateUpdate(170)
            )
            val emission4 = awaitItem()

            testScope.cancel()

            assertThat(emission1.locations[0][0].location).isEqualTo(location1)
            assertThat(emission2.locations[0][1].location).isEqualTo(location2)
            assertThat(emission3.heartRates).isEqualTo(listOf(160))
            assertThat(emission4.heartRates).isEqualTo(listOf(160, 170))

            val expectedDistance = 156.9 * 1_000L
            val tolerance = 0.03
            val lowerBound = (expectedDistance * (1 - tolerance)).roundToInt()
            val upperBound = (expectedDistance * (1 + tolerance)).roundToInt()
            assertThat(emission4.distanceMeters).isBetween(lowerBound, upperBound)

            assertThat(emission4.locations.first()).hasSize(2)
        }
    }
}