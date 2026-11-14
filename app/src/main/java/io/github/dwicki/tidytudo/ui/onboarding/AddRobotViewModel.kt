package io.github.dwicki.tidytudo.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dwicki.tidytudo.AppContainer
import io.github.dwicki.tidytudo.api.ApiError
import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.api.Endpoint
import io.github.dwicki.tidytudo.api.HttpClientFactory
import io.github.dwicki.tidytudo.api.ValetudoClient
import io.github.dwicki.tidytudo.api.getOrNull
import io.github.dwicki.tidytudo.data.Credentials
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.discovery.DiscoveredRobot
import io.github.dwicki.tidytudo.util.RobotAddress
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Where the connection attempt to a chosen robot stands. */
sealed interface ConnectStep {
    data object Idle : ConnectStep
    data class Connecting(val baseUrl: String) : ConnectStep
    data class NeedsCredentials(val baseUrl: String, val rejected: Boolean) : ConnectStep
    data class NeedsCertificate(val baseUrl: String, val sha256: String) : ConnectStep
    data class Failed(val baseUrl: String, val error: ApiError) : ConnectStep
    data class Added(val robotId: String) : ConnectStep
}

enum class ScanState { IDLE, RUNNING, DONE }

data class AddRobotUiState(
    val found: List<DiscoveredRobot> = emptyList(),
    val mdnsSearching: Boolean = true,
    val subnetScan: ScanState = ScanState.IDLE,
    val manualAddress: String = "",
    val manualAddressInvalid: Boolean = false,
    val step: ConnectStep = ConnectStep.Idle,
    val knownUrls: Set<String> = emptySet(),
)

/**
 * Finds robots (mDNS, then optional subnet scan) and walks the user through connecting:
 * basic-auth credentials and self-signed certificates are asked for only when the robot needs them.
 */
class AddRobotViewModel(private val container: AppContainer) : ViewModel() {

    private val state = MutableStateFlow(
        AddRobotUiState(knownUrls = container.robotStore.robots.value.map { it.baseUrl }.toSet()),
    )
    val uiState: StateFlow<AddRobotUiState> = state.asStateFlow()

    private var pendingCredentials: Credentials? = null
    private var pendingPin: String? = null
    private var scanJob: Job? = null

    init {
        startMdns()
    }

    private fun startMdns() {
        viewModelScope.launch {
            container.mdnsDiscovery.discover()
                .catch { state.update { it.copy(mdnsSearching = false) } }
                .collect(::addFound)
        }
        viewModelScope.launch {
            delay(MDNS_PATIENCE_MS)
            state.update { it.copy(mdnsSearching = false) }
        }
    }

    fun scanSubnet() {
        if (scanJob?.isActive == true) return
        state.update { it.copy(subnetScan = ScanState.RUNNING) }
        scanJob = viewModelScope.launch {
            container.subnetScanner.scan().catch { }.collect(::addFound)
            state.update { it.copy(subnetScan = ScanState.DONE) }
        }
    }

    private fun addFound(robot: DiscoveredRobot) = state.update { current ->
        val duplicate = current.found.any {
            it.baseUrl == robot.baseUrl || (robot.systemId.isNotEmpty() && it.systemId == robot.systemId)
        }
        if (duplicate) current else current.copy(found = current.found + robot)
    }

    fun onManualAddressChange(text: String) = state.update { it.copy(manualAddress = text, manualAddressInvalid = false) }

    fun connectManual() {
        val baseUrl = RobotAddress.normalize(state.value.manualAddress)
        if (baseUrl == null) {
            state.update { it.copy(manualAddressInvalid = true) }
            return
        }
        connect(baseUrl)
    }

    fun connect(baseUrl: String) {
        pendingCredentials = null
        pendingPin = null
        attempt(baseUrl)
    }

    fun submitCredentials(username: String, password: String) {
        val baseUrl = (state.value.step as? ConnectStep.NeedsCredentials)?.baseUrl ?: return
        pendingCredentials = Credentials(username, password)
        attempt(baseUrl)
    }

    fun trustCertificate() {
        val step = state.value.step as? ConnectStep.NeedsCertificate ?: return
        pendingPin = step.sha256
        attempt(step.baseUrl)
    }

    fun dismissStep() = state.update { it.copy(step = ConnectStep.Idle) }

    private fun attempt(baseUrl: String) {
        state.update { it.copy(step = ConnectStep.Connecting(baseUrl)) }
        viewModelScope.launch {
            val credentials = pendingCredentials
            val endpoint = Endpoint(baseUrl, credentials?.username, credentials?.password, pendingPin)
            val client = ValetudoClient(baseUrl, HttpClientFactory.create(endpoint))
            val next = when (val version = client.version()) {
                is ApiResult.Success -> ConnectStep.Added(save(baseUrl, client))
                is ApiResult.Failure -> stepFor(baseUrl, version.error, credentials != null)
            }
            state.update { it.copy(step = next) }
        }
    }

    private fun stepFor(baseUrl: String, error: ApiError, triedCredentials: Boolean): ConnectStep = when (error) {
        ApiError.AuthRequired -> ConnectStep.NeedsCredentials(baseUrl, rejected = triedCredentials)
        is ApiError.UntrustedCertificate -> ConnectStep.NeedsCertificate(baseUrl, error.sha256)
        else -> ConnectStep.Failed(baseUrl, error)
    }

    /** Stores the robot, reusing the entry of a robot already known by system id (F-1.7). */
    private suspend fun save(baseUrl: String, client: ValetudoClient): String {
        val info = client.valetudoInfo().getOrNull()
        val robotInfo = client.robotInfo().getOrNull()
        val existing = info?.systemId?.let(container.robotStore::findBySystemId)
        val credentials = pendingCredentials
        val robot = (existing ?: Robot(id = UUID.randomUUID().toString(), baseUrl = baseUrl)).copy(
            baseUrl = baseUrl,
            systemId = info?.systemId ?: existing?.systemId.orEmpty(),
            manufacturer = robotInfo?.manufacturer.orEmpty(),
            model = robotInfo?.modelName.orEmpty(),
            pinnedSha256 = pendingPin,
            hasCredentials = credentials != null,
        )
        if (credentials != null) container.vault.save(robot.id, credentials) else container.vault.delete(robot.id)
        container.sessions.invalidate(robot.id)
        container.robotStore.upsert(robot)
        container.robotStore.select(robot.id)
        return robot.id
    }

    private companion object {
        const val MDNS_PATIENCE_MS = 6_000L
    }
}
