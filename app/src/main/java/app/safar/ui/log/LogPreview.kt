package app.safar.ui.log

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.safar.core.LogKind
import app.safar.core.LogRecord
import app.safar.data.ThemeMode
import app.safar.protocol.ChecksumMode
import app.safar.protocol.ClusterData
import app.safar.protocol.encodeCluster
import app.safar.protocol.toHex
import app.safar.ui.components.rememberToastState
import app.safar.ui.theme.SafarTheme

// Synthetic preview data only.
private const val NOW = 1_760_000_000_000L
private val clusterHex = toHex(encodeCluster(ClusterData(42, 12_345, 142.6, 8.1, 4), ChecksumMode.SUM))

private val records = listOf(
    LogRecord(1, NOW - 3_000, LogKind.EVENT, name = "connected", detail = mapOf("mtu" to "65")),
    LogRecord(2, NOW - 2_000, LogKind.RX, '7', clusterHex, name = "Cluster"),
    LogRecord(3, NOW - 1_000, LogKind.ERROR, name = "write failed", detail = mapOf("status" to "133")),
)

@Composable
private fun Frame(state: LogScreenState, dark: Boolean) = SafarTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
    LogContent(state, LogActions(), rememberToastState())
}

@Preview(heightDp = 1100)
@Composable
private fun LogListPreview() = Frame(
    LogScreenState(total = 3, matching = 3, rows = filterRecords(records, LogFilter()).map { rowOf(it) }, sessionActive = true),
    dark = false,
)

@Preview(heightDp = 1300)
@Composable
private fun LogDetailPreview() = Frame(LogScreenState(total = 3, selected = records[1], debugLogOn = true), dark = true)

@Preview(heightDp = 1100, fontScale = 1.4f)
@Composable
private fun LogExportPreview() = Frame(
    LogScreenState(export = ExportSheet(hideNames = true, busy = false, preview = ExportPreview("safar-log.jsonl", 12, 4_200, listOf("{\"kind\":\"header\"}")))),
    dark = false,
)
