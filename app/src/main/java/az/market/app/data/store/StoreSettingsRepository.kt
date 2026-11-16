package az.market.app.data.store

import az.market.app.core.di.IoDispatcher
import az.market.app.data.remote.SupabaseProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Connection check result shown in the app (debug panel) and used by diagnostics. */
sealed interface BackendStatus {
    data object Checking : BackendStatus
    data object NotConfigured : BackendStatus
    data class Connected(val storeName: String, val categoryCount: Int, val latencyMs: Long) : BackendStatus
    data class Unreachable(val reason: String) : BackendStatus
}

interface StoreSettingsRepository {
    /** Current settings followed by live Realtime changes; falls back to defaults if offline. */
    fun observeSettings(): Flow<StoreSettings>
    suspend fun checkBackend(): BackendStatus
}

@Singleton
class SupabaseStoreSettingsRepository @Inject constructor(
    private val provider: SupabaseProvider,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : StoreSettingsRepository {

    private val remote: StoreRemoteDataSource? by lazy { provider.client?.let(::StoreRemoteDataSource) }

    override fun observeSettings(): Flow<StoreSettings> = flow {
        val source = remote
        if (source == null) {
            emit(StoreSettings())
            return@flow
        }
        emit(source.fetchSettings())
        emitAll(source.settingsUpdates())
    }.catch { emit(StoreSettings()) }.flowOn(io)

    override suspend fun checkBackend(): BackendStatus {
        val source = remote ?: return BackendStatus.NotConfigured
        return withContext(io) { runCatching {
            val started = System.nanoTime()
            val settings = source.fetchSettings()
            val categories = source.fetchCategories()
            BackendStatus.Connected(settings.storeName, categories.size, (System.nanoTime() - started) / 1_000_000)
        }.getOrElse {
            if (it is CancellationException) throw it
            BackendStatus.Unreachable(it.message ?: it::class.simpleName.orEmpty())
        } }
    }
}
