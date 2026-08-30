package com.sbi.surakshasathi.feature.messagescan

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageClassification
import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageSource
import com.sbi.surakshasathi.feature.messagescan.domain.model.RawIncomingMessage
import com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository
import com.sbi.surakshasathi.feature.messagescan.domain.usecase.ClassifyMessageUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClassifyMessageUseCaseTest {

    private val repository = mockk<MessageRepository>()
    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = TestDispatcherProvider(testDispatcher)
    private lateinit var useCase: ClassifyMessageUseCase

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        useCase = ClassifyMessageUseCase(repository, dispatchers)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test usecase executes and returns success`() = kotlinx.coroutines.test.runTest(testDispatcher) {
        val raw = RawIncomingMessage(
            body = "Warning: your YONO app needs updating.",
            sender = "BANKYONO",
            source = MessageSource.SMS
        )

        val message = Message(
            id = 1L,
            body = raw.body,
            bodyHash = "hash123",
            sender = raw.sender,
            source = raw.source,
            receivedAtMillis = raw.receivedAtMillis,
            classification = MessageClassification.SUSPICIOUS,
            riskScore = 0.5f
        )

        coEvery { repository.classifyAndStore(raw) } returns Result.Success(message)

        val result = useCase(raw)

        assertEquals(Result.Success(message), result)
    }
}
