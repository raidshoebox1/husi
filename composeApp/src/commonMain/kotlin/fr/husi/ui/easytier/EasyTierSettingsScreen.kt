package fr.husi.ui.easytier

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.Key
import fr.husi.bg.easytier.EasyTierConfig
import fr.husi.bg.easytier.EasyTierManager
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.SwipeableSnackbarHost
import fr.husi.compose.material3.Text
import fr.husi.compose.paddingExceptBottom
import fr.husi.database.DataStore
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.arrow_back
import fr.husi.resources.easytier_clear_logs
import fr.husi.resources.easytier_discovered_cidrs
import fr.husi.resources.easytier_enable
import fr.husi.resources.easytier_enable_summary
import fr.husi.resources.easytier_hostname
import fr.husi.resources.easytier_listeners
import fr.husi.resources.easytier_log_level
import fr.husi.resources.easytier_logs
import fr.husi.resources.easytier_mtu
import fr.husi.resources.easytier_network_info
import fr.husi.resources.easytier_network_name
import fr.husi.resources.easytier_network_secret
import fr.husi.resources.easytier_no_tun
import fr.husi.resources.easytier_no_tun_summary
import fr.husi.resources.easytier_peers
import fr.husi.resources.easytier_rpc_port
import fr.husi.resources.easytier_settings
import fr.husi.resources.easytier_socks5_port
import fr.husi.resources.easytier_status
import fr.husi.resources.easytier_status_disabled
import fr.husi.resources.easytier_status_running
import fr.husi.resources.easytier_status_stopped
import fr.husi.resources.easytier_started
import fr.husi.resources.easytier_start_failed
import fr.husi.resources.easytier_stopped
import fr.husi.resources.easytier_virtual_ip
import fr.husi.resources.back
import fr.husi.resources.not_set
import fr.husi.resources.start
import fr.husi.resources.stop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
fun EasyTierSettingsScreen(
    onBackPress: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInsets = WindowInsets.safeDrawing

    val enabled by DataStore.configurationStore
        .booleanFlow(Key.EASYTIER_ENABLED, false)
        .collectAsStateWithLifecycle(false)
    val networkName by DataStore.configurationStore
        .stringFlow(Key.EASYTIER_NETWORK_NAME, "")
        .collectAsStateWithLifecycle("")
    val networkSecret by DataStore.configurationStore
        .stringFlow(Key.EASYTIER_NETWORK_SECRET, "")
        .collectAsStateWithLifecycle("")
    val hostname by DataStore.configurationStore
        .stringFlow(Key.EASYTIER_HOSTNAME, "")
        .collectAsStateWithLifecycle("")
    val virtualIp by DataStore.configurationStore
        .stringFlow(Key.EASYTIER_VIRTUAL_IP, "")
        .collectAsStateWithLifecycle("")
    val peers by DataStore.configurationStore
        .stringFlow(Key.EASYTIER_PEERS, "")
        .collectAsStateWithLifecycle("")
    val listeners by DataStore.configurationStore
        .stringFlow(Key.EASYTIER_LISTENERS, "")
        .collectAsStateWithLifecycle("")
    val socks5Port by DataStore.configurationStore
        .intFlow(Key.EASYTIER_SOCKS5_PORT, EasyTierConfig.DEFAULT_SOCKS5_PORT)
        .collectAsStateWithLifecycle(EasyTierConfig.DEFAULT_SOCKS5_PORT)
    val rpcPort by DataStore.configurationStore
        .intFlow(Key.EASYTIER_RPC_PORT, EasyTierConfig.DEFAULT_RPC_PORT)
        .collectAsStateWithLifecycle(EasyTierConfig.DEFAULT_RPC_PORT)
    val noTun by DataStore.configurationStore
        .booleanFlow(Key.EASYTIER_NO_TUN, true)
        .collectAsStateWithLifecycle(true)
    val mtu by DataStore.configurationStore
        .intFlow(Key.EASYTIER_MTU, 0)
        .collectAsStateWithLifecycle(0)
    val logLevel by DataStore.configurationStore
        .stringFlow(Key.EASYTIER_LOG_LEVEL, "info")
        .collectAsStateWithLifecycle("info")

    var statusText by remember { mutableStateOf(EasyTierManager.getStatusText()) }
    var logsText by remember { mutableStateOf(EasyTierManager.getLogs()) }
    var networkInfoText by remember { mutableStateOf(EasyTierManager.getNetworkInfo()) }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CapsuleTopBar(
                title = { Text(stringResource(Res.string.easytier_settings)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        onClick = onBackPress,
                    )
                },
                scrollBehavior = scrollBehavior,
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        snackbarHost = { SwipeableSnackbarHost(snackbarState) },
    ) { innerPadding ->
        ProvidePreferenceLocals {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .paddingExceptBottom(innerPadding)
                    .verticalScroll(rememberScrollState()),
            ) {
            // Enable switch
            SwitchPreference(
                value = enabled,
                onValueChange = {
                    DataStore.easyTierEnabled = it
                },
                title = { Text(stringResource(Res.string.easytier_enable)) },
                summary = { Text(stringResource(Res.string.easytier_enable_summary)) },
            )
            PreferenceDivider()

            // Status
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                colors = CardDefaults.cardColors(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(Res.string.easytier_status),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    val statusRes = when (statusText) {
                        "running" -> Res.string.easytier_status_running
                        "disabled" -> Res.string.easytier_status_disabled
                        else -> Res.string.easytier_status_stopped
                    }
                    Text(
                        text = stringResource(statusRes),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Network settings
            TextFieldPreference(
                value = networkName,
                onValueChange = {
                    DataStore.easyTierNetworkName = it
                },
                title = { Text(stringResource(Res.string.easytier_network_name)) },
                textToValue = { it },
                summary = { Text(networkName.ifBlank { stringResource(Res.string.not_set) }) },
                valueToText = { it },
            )
            PreferenceDivider()

            TextFieldPreference(
                value = networkSecret,
                onValueChange = {
                    DataStore.easyTierNetworkSecret = it
                },
                title = { Text(stringResource(Res.string.easytier_network_secret)) },
                textToValue = { it },
                summary = { Text(if (networkSecret.isBlank()) stringResource(Res.string.not_set) else "*****") },
                valueToText = { it },
            )
            PreferenceDivider()

            TextFieldPreference(
                value = hostname,
                onValueChange = {
                    DataStore.easyTierHostname = it
                },
                title = { Text(stringResource(Res.string.easytier_hostname)) },
                textToValue = { it },
                summary = { Text(hostname.ifBlank { stringResource(Res.string.not_set) }) },
                valueToText = { it },
            )
            PreferenceDivider()

            TextFieldPreference(
                value = virtualIp,
                onValueChange = {
                    DataStore.easyTierVirtualIp = it
                },
                title = { Text(stringResource(Res.string.easytier_virtual_ip)) },
                textToValue = { it },
                summary = { Text(virtualIp.ifBlank { stringResource(Res.string.not_set) }) },
                valueToText = { it },
            )
            PreferenceDivider()

            TextFieldPreference(
                value = peers,
                onValueChange = {
                    DataStore.easyTierPeers = it
                },
                title = { Text(stringResource(Res.string.easytier_peers)) },
                textToValue = { it },
                summary = {
                    Text(
                        if (peers.isBlank()) {
                            "e.g. tcp://192.168.1.10:11010 (required to join a network)"
                        } else {
                            peers.lines().joinToString(", ")
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                valueToText = { it },
            )
            PreferenceDivider()

            TextFieldPreference(
                value = listeners,
                onValueChange = {
                    DataStore.easyTierListeners = it
                },
                title = { Text(stringResource(Res.string.easytier_listeners)) },
                textToValue = { it },
                summary = {
                    Text(
                        if (listeners.isBlank()) EasyTierConfig.DEFAULT_LISTENER
                        else listeners.lines().joinToString(", "),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                valueToText = { it },
            )
            PreferenceDivider()

            TextFieldPreference(
                value = socks5Port,
                onValueChange = {
                    DataStore.easyTierSocks5Port = it
                },
                title = { Text(stringResource(Res.string.easytier_socks5_port)) },
                textToValue = { it.toIntOrNull() ?: EasyTierConfig.DEFAULT_SOCKS5_PORT },
                summary = { Text(socks5Port.toString()) },
                valueToText = { it.toString() },
            )
            PreferenceDivider()

            TextFieldPreference(
                value = rpcPort,
                onValueChange = {
                    DataStore.easyTierRpcPort = it
                },
                title = { Text(stringResource(Res.string.easytier_rpc_port)) },
                textToValue = { it.toIntOrNull() ?: EasyTierConfig.DEFAULT_RPC_PORT },
                summary = { Text(rpcPort.toString()) },
                valueToText = { it.toString() },
            )
            PreferenceDivider()

            // No TUN switch
            SwitchPreference(
                value = noTun,
                onValueChange = {
                    DataStore.easyTierNoTun = it
                },
                title = { Text(stringResource(Res.string.easytier_no_tun)) },
                summary = { Text(stringResource(Res.string.easytier_no_tun_summary)) },
            )
            PreferenceDivider()

            // MTU
            TextFieldPreference(
                value = mtu,
                onValueChange = {
                    DataStore.easyTierMtu = it
                },
                title = { Text(stringResource(Res.string.easytier_mtu)) },
                textToValue = { it.toIntOrNull() ?: 0 },
                summary = { Text(if (mtu > 0) mtu.toString() else stringResource(Res.string.not_set)) },
                valueToText = { it.toString() },
            )
            PreferenceDivider()

            // Log level
            TextFieldPreference(
                value = logLevel,
                onValueChange = {
                    DataStore.easyTierLogLevel = it
                },
                title = { Text(stringResource(Res.string.easytier_log_level)) },
                textToValue = { it },
                summary = { Text(logLevel) },
                valueToText = { it },
            )

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                EasyTierManager.start()
                            }
                            statusText = EasyTierManager.getStatusText()
                            networkInfoText = EasyTierManager.getNetworkInfo()
                            logsText = EasyTierManager.getLogs()
                            snackbarState.showSnackbar(
                                if (result) resolveRepository().getString(Res.string.easytier_started)
                                else resolveRepository().getString(
                                    Res.string.easytier_start_failed,
                                    EasyTierManager.getLastError() ?: "unknown",
                                ),
                            )
                        }
                    },
                    enabled = enabled && networkName.isNotBlank(),
                ) {
                    Text(stringResource(Res.string.start))
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { EasyTierManager.stop() }
                            statusText = EasyTierManager.getStatusText()
                            networkInfoText = EasyTierManager.getNetworkInfo()
                            snackbarState.showSnackbar(resolveRepository().getString(Res.string.easytier_stopped))
                        }
                    },
                ) {
                    Text(stringResource(Res.string.stop))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp, 0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        logsText = EasyTierManager.getLogs()
                        networkInfoText = EasyTierManager.getNetworkInfo()
                        statusText = EasyTierManager.getStatusText()
                    },
                ) {
                    Text(stringResource(Res.string.easytier_logs))
                }
                OutlinedButton(
                    onClick = {
                        EasyTierManager.clearLogs()
                        logsText = EasyTierManager.getLogs()
                    },
                ) {
                    Text(stringResource(Res.string.easytier_clear_logs))
                }
                OutlinedButton(
                    onClick = {
                        networkInfoText = EasyTierManager.getNetworkInfo()
                        statusText = EasyTierManager.getStatusText()
                    },
                ) {
                    Text(stringResource(Res.string.easytier_network_info))
                }
            }

            // Discovered CIDRs
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                colors = CardDefaults.cardColors(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(Res.string.easytier_discovered_cidrs),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    val cidrs = EasyTierManager.getMeshCidrs()
                    if (cidrs.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.not_set),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        cidrs.forEach { cidr ->
                            Text(
                                text = cidr,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            // Network info
            if (networkInfoText.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                    colors = CardDefaults.cardColors(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(Res.string.easytier_network_info),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = networkInfoText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            // Logs
            if (logsText.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                    colors = CardDefaults.cardColors(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(Res.string.easytier_logs),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = logsText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 20,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            }
        }
    }
}
