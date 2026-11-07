package dev.filpper.companion.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.filpper.companion.data.BleConnectionRepositoryImpl
import dev.filpper.companion.data.mock.MockFlipperDeviceInfoRepository
import dev.filpper.companion.data.mock.MockFlipperFileRepository
import dev.filpper.companion.data.ThemeRepositoryImpl
import dev.filpper.companion.domain.repository.BleConnectionRepository
import dev.filpper.companion.domain.repository.FlipperDeviceInfoRepository
import dev.filpper.companion.domain.repository.FlipperFileRepository
import dev.filpper.companion.domain.repository.ThemeRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindBleConnection(impl: BleConnectionRepositoryImpl): BleConnectionRepository

    @Binds @Singleton
    abstract fun bindDeviceInfo(impl: MockFlipperDeviceInfoRepository): FlipperDeviceInfoRepository

    @Binds @Singleton
    abstract fun bindFiles(impl: MockFlipperFileRepository): FlipperFileRepository

    @Binds @Singleton
    abstract fun bindTheme(impl: ThemeRepositoryImpl): ThemeRepository
}
