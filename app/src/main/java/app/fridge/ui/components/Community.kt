package app.fridge.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.fridge.R
import app.fridge.ui.theme.LocalPalette
import app.fridge.ui.theme.LocalType

/** The StillCraft bot: deep links file a bug or an idea for this app, or follow its releases. */
object Community {
    private const val BOT = "https://t.me/ArcaneWhisperBot?start="
    const val SLUG = "fridge"
    const val BUG = "${BOT}bug_$SLUG"
    const val IDEA = "${BOT}wish_$SLUG"
    const val FOLLOW = "${BOT}f_$SLUG"
    const val CHAT = "https://t.me/+Q4gFEDk8O842OGVi"
}

/** Report a bug, suggest an idea, follow updates, join the chat; each opens Telegram. */
@Composable
fun CommunityLinks() {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val t = LocalType.current
    fun open(url: String) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    Paper(p.paper, magnet = p.magnetBlue) {
        for ((icon, label, sub, url) in listOf(
            Link("🐞", R.string.community_bug, R.string.community_bug_sub, Community.BUG),
            Link("💡", R.string.community_idea, R.string.community_idea_sub, Community.IDEA),
            Link("🔔", R.string.community_follow, R.string.community_follow_sub, Community.FOLLOW),
            Link("💬", R.string.community_chat, R.string.community_chat_sub, Community.CHAT),
        )) {
            Row(Modifier.fillMaxWidth().clickable { open(url) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(icon, style = t.title, modifier = Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(label), style = t.body, color = p.ink)
                    Text(stringResource(sub), style = t.label, color = p.faint)
                }
                Text("›", style = t.title, color = p.blueInk)
            }
        }
    }
}

private data class Link(val icon: String, val label: Int, val sub: Int, val url: String)
