package dev.filpper.companion.presentation.pages.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.filpper.companion.data.flipper.rpc.RpcStatus
import dev.filpper.companion.domain.model.BleConnectionState
import dev.filpper.companion.domain.repository.FlipperRepository
import dev.filpper.companion.domain.usecase.ConnectFlipperUseCase
import dev.filpper.companion.domain.usecase.DisconnectFlipperUseCase
import dev.filpper.companion.domain.usecase.GetFlipperInfoUseCase
import dev.filpper.companion.domain.usecase.PingUseCase
import dev.filpper.companion.domain.usecase.StartBleScanUseCase
import dev.filpper.companion.domain.usecase.StopBleScanUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    flipperRepository: FlipperRepository,
    private val startScan: StartBleScanUseCase,
    private val stopScan: StopBleScanUseCase,
    private val connect: ConnectFlipperUseCase,
    private val disconnect: DisconnectFlipperUseCase,
    private val ping: PingUseCase,
    private val getInfo: GetFlipperInfoUseCase
) : ViewModel() {

    private val info = MutableStateFlow<DeviceInfoState>(DeviceInfoState.None)

    val state: StateFlow<DeviceUiState> = combine(
        flipperRepository.connection,
        flipperRepository.discoveredDevices,
        flipperRepository.isScanning,
        info
    ) { connection, devices, scanning, deviceInfo ->
        DeviceUiState(connection, devices, scanning, deviceInfo)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DeviceUiState())

    init {
        viewModelScope.launch {
            combine(
                flipperRepository.connection, flipperRepository.rpcStatus
            ) { connection, rpcStatus ->
                connection is BleConnectionState.Connected && rpcStatus == RpcStatus.ACTIVE
            }
                .distinctUntilChanged()
                .collectLatest { isReady ->
                    if (isReady) loadInfo() else { info.value = DeviceInfoState.None }
                }
        }
    }

    fun onIntent(intent: DeviceIntent) {
        viewModelScope.launch {
            when (intent) {
                DeviceIntent.StartScan -> startScan()
                DeviceIntent.StopScan -> stopScan()
                is DeviceIntent.SelectDevice -> connect(intent.device)
                DeviceIntent.Disconnect -> disconnect()
                DeviceIntent.RefreshInfo -> {
                    if (state.value.connection is BleConnectionState.Connected) loadInfo()
                }
                DeviceIntent.Ping -> ping()
            }
        }
    }

    private suspend fun loadInfo() {
        info.value = DeviceInfoState.Loading
        try {
            info.value = DeviceInfoState.Ready(getInfo())
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            info.value = DeviceInfoState.Failed
        }
    }
}
