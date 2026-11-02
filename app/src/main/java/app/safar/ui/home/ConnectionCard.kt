package app.safar.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.safar.core.LinkState
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.DashedRule
import app.safar.ui.components.DevOnly
import app.safar.ui.components.DeveloperLine
import app.safar.ui.components.DisplayTitle
import app.safar.ui.components.InkButton
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.Stamp
import app.safar.ui.components.TextAction
import app.safar.ui.components.liveAge
import app.safar.ui.components.rememberNow
import app.safar.ui.copy.LinkCopy
import app.safar.ui.copy.Phrases
import app.safar.ui.theme.Atlas

/** What the connection card can trigger. */
class ConnActions(
    val openRide: () -> Unit,
    val connectSaved: () -> Unit,
    val openPair: () -> Unit,
    val enableBluetooth: () -> Unit,
)

/**
 * The one large connection state on Today (home-conn.js `connHero`): headline and one calm sentence from
 * [LinkCopy], the link drawing, then what the rider can do. Technical lines are Developer mode only.
 */
@Composable
fun ConnectionCard(conn: ConnUi, autoRideOn: Boolean, actions: ConnActions, modifier: Modifier = Modifier) {
    val c = Atlas.colors
    AtlasCard(modifier.padding(bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                val idle = conn.kind == ConnKind.IDLE || conn.kind == ConnKind.BLUETOOTH_OFF
                DisplayTitle(
                    conn.title, null, Atlas.type.displayMedium.copy(fontSize = Atlas.type.displayMedium.fontSize * 1.05f),
                    Modifier.semantics { heading() }, color = if (idle) c.rust else c.ink,
                )
                BasicText(conn.subtitle, Modifier.padding(top = 4.dp), style = Atlas.type.serifItalic.copy(color = c.ink2))
            }
            Stamp(conn.stamp, tone = conn.tone)
        }
        LinkArt(conn.kind, conn.family)
        ConnFoot(conn, autoRideOn, actions)
    }
}

@Composable
private fun ConnFoot(conn: ConnUi, autoRideOn: Boolean, actions: ConnActions) {
    when (conn.kind) {
        ConnKind.CONNECTED -> ConnectedFoot(conn, autoRideOn, actions.openRide)
        ConnKind.RECONNECTING -> Backoff(conn.attempt, conn.nextAttemptAt ?: 0L)
        ConnKind.IDLE -> if (conn.savedAddress != null) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InkButton("Connect", actions.connectSaved, Modifier.weight(1f), icon = AtlasIcons.bt)
                SecondaryButton("Pair another", actions.openPair, Modifier.weight(1f))
            }
        } else {
            InkButton(Phrases.LOOK_FOR_BIKE, actions.openPair, Modifier.fillMaxWidth().padding(top = 6.dp), icon = AtlasIcons.search)
        }
        ConnKind.SEARCHING -> Column {
            DevOnly { DeveloperLink(conn) }
            TextAction(if (conn.savedAddress != null) "Pair another bike" else Phrases.LOOK_FOR_BIKE, actions.openPair)
        }
        ConnKind.CONNECTING -> DevOnly { DeveloperLink(conn) }
        ConnKind.BLUETOOTH_OFF -> InkButton(Phrases.TURN_ON_BLUETOOTH, actions.enableBluetooth, Modifier.fillMaxWidth().padding(top = 6.dp), icon = AtlasIcons.bt)
    }
}

@Composable
private fun ConnectedFoot(conn: ConnUi, autoRideOn: Boolean, openRide: () -> Unit) {
    val c = Atlas.colors
    DashedRule(Modifier.padding(top = 4.dp, bottom = 10.dp), thickness = 1.dp, dash = 4.dp, gap = 3.dp)
    DevOnly { DeveloperLink(conn) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PrimaryButton("Ride mode", openRide, Modifier.fillMaxWidth(), icon = AtlasIcons.ride)
        BasicText(
            if (autoRideOn) "Opens by itself when you start riding." else "Auto-open is off in Settings.",
            Modifier.padding(top = 4.dp),
            style = Atlas.type.uiSmall.copy(color = c.ink3, textAlign = TextAlign.Center),
        )
    }
}

/**
 * Developer mode: the bike's Bluetooth name and the link detail ("MTU 65 · notify on · last frame 3 s ago",
 * "back-off ..."). Reads the clock here only, so its tick recomposes this line alone.
 */
@Composable
private fun DeveloperLink(conn: ConnUi) {
    val now by rememberNow()
    val detail = when (conn.link) {
        LinkState.Connected -> listOfNotNull(
            conn.mtu?.let { "MTU $it" },
            "notify on",
            "last frame ${conn.lastFrameAt?.let { liveAge(it) } ?: "waiting"}",
        ).joinToString(" · ")
        else -> LinkCopy.developerDetail(conn.link, now)
    }
    DeveloperLine(listOfNotNull(conn.bikeName, detail).joinToString(" · "), Modifier.padding(bottom = 4.dp))
}
