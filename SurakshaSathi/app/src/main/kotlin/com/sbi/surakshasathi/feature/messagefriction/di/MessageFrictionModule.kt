package com.sbi.surakshasathi.feature.messagefriction.di

import com.sbi.surakshasathi.feature.messagefriction.data.remote.GuardianChatApi
import com.sbi.surakshasathi.feature.messagefriction.data.repository.GuardianChatRepositoryImpl
import com.sbi.surakshasathi.feature.messagefriction.domain.repository.GuardianChatRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/** Hilt module for the Adaptive Friction engine (see [com.sbi.surakshasathi.feature.messagefriction]) —
 * only the Guardian AI chat needs network wiring; the rest of the flow reads/writes through
 * [com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository], already bound
 * elsewhere. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MessageFrictionModule {
    @Binds
    @Singleton
    abstract fun bindGuardianChatRepository(impl: GuardianChatRepositoryImpl): GuardianChatRepository

    companion object {
        @Provides
        @Singleton
        fun provideGuardianChatApi(retrofit: Retrofit): GuardianChatApi = retrofit.create(GuardianChatApi::class.java)
    }
}
