package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.openUrl

private const val DONATE_URL = "https://www.ifdian.net/a/bichencao?utm_source=copylink&utm_medium=link"

@Composable
fun SettingDonatePage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(R.string.donate_page_title))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { paddings ->
        Column(
            modifier = Modifier
                .padding(paddings)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DonateMethodsCardGroup()

            CardGroup(
                modifier = Modifier.fillMaxWidth(),
            ) {
                item(
                    headlineContent = { Text("谢谢你愿意支持露露小手机") },
                    supportingContent = { Text("赞助列表已清空，这里只保留佳辞自己的赞助入口。") },
                )
            }
        }
    }
}

@Composable
private fun DonateMethodsCardGroup() {
    val context = LocalContext.current
    CardGroup(
        modifier = Modifier.fillMaxWidth(),
        title = { Text(stringResource(R.string.donate_page_donation_methods)) },
    ) {
        item(
            onClick = { context.openUrl(DONATE_URL) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.afdian),
                    contentDescription = null,
                )
            },
            supportingContent = { Text("打开佳辞的爱发电主页") },
            headlineContent = { Text("爱发电") },
        )
    }
}
