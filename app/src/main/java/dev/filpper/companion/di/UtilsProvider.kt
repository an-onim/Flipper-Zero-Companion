package dev.filpper.companion.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.filpper.companion.utils.PermissionChecker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class UtilsProvider {

    @Provides
    @Singleton
    fun providePermissionChecker(
        @ApplicationContext context: Context
    ) = PermissionChecker(context)


}