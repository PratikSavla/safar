package app.safar.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceSeedTest {
    private class FakeServiceRepository : ServiceRepository {
        val state = MutableStateFlow<List<ServiceItem>>(emptyList())
        override val items: Flow<List<ServiceItem>> = state
        override suspend fun upsert(item: ServiceItem): Long {
            val id = state.value.size + 1L
            state.value = state.value + item.copy(id = id)
            return id
        }
        override suspend fun markDone(id: Long, odoKm: Int, atMillis: Long) = Unit
        override suspend fun delete(id: Long) = Unit
    }

    @Test
    fun seedsOnceWhenEmpty() = runTest {
        val repo = FakeServiceRepository()
        assertTrue(repo.seedDefaultsIfEmpty(odometerKm = 12261, atMillis = 1_000))
        assertEquals(5, repo.state.value.size)
        assertEquals(listOf(12261), repo.state.value.map { it.lastDoneOdoKm }.distinct())
        assertFalse(repo.seedDefaultsIfEmpty(odometerKm = 13000, atMillis = 2_000))
        assertEquals(5, repo.state.value.size)
    }
}
