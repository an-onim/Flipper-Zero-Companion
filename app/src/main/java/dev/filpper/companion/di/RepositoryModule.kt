package dev.filpper.companion.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.filpper.companion.data.mock.MockFlipperFileRepository
import dev.filpper.companion.data.repository.FlipperRepositoryImpl
import dev.filpper.companion.data.repository.ThemeRepositoryImpl
import dev.filpper.companion.domain.repository.FlipperRepository
import dev.filpper.companion.domain.repository.FlipperFileRepository
import dev.filpper.companion.domain.repository.ThemeRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBleConnection(impl: FlipperRepositoryImpl): FlipperRepository

    @Binds
    @Singleton
    abstract fun bindFiles(impl: MockFlipperFileRepository): FlipperFileRepository

    @Binds
    @Singleton
    abstract fun bindTheme(impl: ThemeRepositoryImpl): ThemeRepository
}
