package com.sbi.surakshasathi.feature.awareness

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.awareness.data.local.dao.AdvisoryDao
import com.sbi.surakshasathi.feature.awareness.data.local.entity.AdvisoryEntity
import com.sbi.surakshasathi.feature.awareness.data.remote.AdvisoryDto
import com.sbi.surakshasathi.feature.awareness.data.remote.AwarenessApi
import com.sbi.surakshasathi.feature.awareness.data.repository.AdvisoryRepositoryImpl
import com.sbi.surakshasathi.feature.awareness.data.repository.BundledAdvisories
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Mirrors [com.sbi.surakshasathi.feature.awareness.data.repository.LessonRepositoryImpl]'s
 * seed-then-refresh-then-silently-fall-back shape — games have no equivalent test since game
 * content is bundled-only with no backend refresh (§7c Phase 7 plan).
 */
class AdvisoryRepositoryImplTest {
    private val advisoryDao = mockk<AdvisoryDao>()
    private val awarenessApi = mockk<AwarenessApi>()
    private val repository = AdvisoryRepositoryImpl(advisoryDao, awarenessApi)

    @Test
    fun `seeds bundled advisories on first run when the table is empty`() =
        runTest {
            coEvery { advisoryDao.countForLanguage("en") } returns 0
            val seeded = slot<List<AdvisoryEntity>>()
            coEvery { advisoryDao.upsertAll(capture(seeded)) } returns Unit
            coEvery { awarenessApi.getAdvisories("en") } throws IOException("offline")

            val result = repository.refreshAdvisories("en")

            assertTrue(result is Result.Success)
            assertEquals(BundledAdvisories.ENGLISH.size, seeded.captured.size)
        }

    @Test
    fun `does not reseed when advisories already exist for the language`() =
        runTest {
            coEvery { advisoryDao.countForLanguage("en") } returns 5
            coEvery { awarenessApi.getAdvisories("en") } throws IOException("offline")

            repository.refreshAdvisories("en")

            coVerify(exactly = 0) { advisoryDao.upsertAll(any()) }
        }

    @Test
    fun `a successful network refresh overwrites with live advisories`() =
        runTest {
            coEvery { advisoryDao.countForLanguage("en") } returns 5
            val liveDto =
                AdvisoryDto(
                    id = "adv_live_test",
                    title = "Live Advisory",
                    body = "Body text.",
                    category = "PHISHING",
                    personaTags = listOf("general"),
                    language = "en",
                    sourceLabel = "Test Source",
                    sourceUrl = null,
                )
            coEvery { awarenessApi.getAdvisories("en") } returns listOf(liveDto)
            val upserted = slot<List<AdvisoryEntity>>()
            coEvery { advisoryDao.upsertAll(capture(upserted)) } returns Unit

            val result = repository.refreshAdvisories("en")

            assertTrue(result is Result.Success)
            assertEquals(1, upserted.captured.size)
            assertEquals("adv_live_test", upserted.captured.first().id)
        }

    @Test
    fun `a failed network refresh is a silent success, not a user-facing error`() =
        runTest {
            coEvery { advisoryDao.countForLanguage("en") } returns 5
            coEvery { awarenessApi.getAdvisories("en") } throws IOException("offline")

            val result = repository.refreshAdvisories("en")

            assertTrue(result is Result.Success)
        }
}
