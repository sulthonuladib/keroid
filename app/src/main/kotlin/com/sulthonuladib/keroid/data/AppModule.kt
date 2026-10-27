package com.sulthonuladib.keroid.data

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds abstract fun remoteMachines(repository: KeroRepository): RemoteMachineFactory
    @Binds abstract fun libraryVault(vault: CredentialVault): LibraryVault
    @Binds abstract fun identityInspector(inspector: GoIdentityInspector): IdentityInspector
    @Binds abstract fun preferences(settings: AppSettings): Preferences

    companion object {
        @Provides
        @Singleton
        fun appSettings(@ApplicationContext context: Context): AppSettings = AppSettings(context)
    }
}
