package com.sbi.surakshasathi.feature.messagescan.di

import com.sbi.surakshasathi.BuildConfig
import com.sbi.surakshasathi.feature.messagescan.data.classifier.TraiDltValidator
import com.sbi.surakshasathi.feature.messagescan.data.classifier.TraiDltValidatorImpl
import com.sbi.surakshasathi.feature.messagescan.data.ingestion.DefaultHandlerSmsStrategy
import com.sbi.surakshasathi.feature.messagescan.data.ingestion.NotificationOnlySmsStrategy
import com.sbi.surakshasathi.feature.messagescan.data.ingestion.SmsIngestionStrategy
import com.sbi.surakshasathi.feature.messagescan.data.repository.MessageRepositoryImpl
import com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the Message Scan feature.
 *
 * Key bindings:
 * - [MessageRepository] → [MessageRepositoryImpl] (always real — no fake default)
 * - [SmsIngestionStrategy] → selected at BUILD TIME via BuildConfig.SMS_STRATEGY
 *   (not runtime injection — avoids permission confusion)
 * - [TraiDltValidator] → [TraiDltValidatorImpl]
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MessageScanModule {
    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    @Binds
    @Singleton
    abstract fun bindTraiDltValidator(impl: TraiDltValidatorImpl): TraiDltValidator

    companion object {
        /**
         * Selects the SMS ingestion strategy based on BuildConfig.SMS_STRATEGY.
         * - "NOTIFICATION_ONLY" → [NotificationOnlySmsStrategy] (default, Play-publishable)
         * - "DEFAULT_HANDLER"  → [DefaultHandlerSmsStrategy] (restricted, enterprise)
         */
        @Provides
        @Singleton
        fun provideSmsIngestionStrategy(
            notificationOnly: NotificationOnlySmsStrategy,
            defaultHandler: DefaultHandlerSmsStrategy,
        ): SmsIngestionStrategy =
            when (BuildConfig.SMS_STRATEGY) {
                "DEFAULT_HANDLER" -> defaultHandler
                else -> notificationOnly // Safe default
            }
    }
}
