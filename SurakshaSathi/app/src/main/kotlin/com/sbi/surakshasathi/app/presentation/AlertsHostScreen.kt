package com.sbi.surakshasathi.app.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.feature.frauddashboard.presentation.regionalalerts.RegionalDigestScreen
import com.sbi.surakshasathi.feature.messagescan.presentation.MessageScanContent
import com.sbi.surakshasathi.feature.messagescan.presentation.MessageScanViewModel
import com.sbi.surakshasathi.feature.messagescan.presentation.MessageVerificationContent

/**
 * Bottom-nav "Alerts" tab shell: combines the device's own flagged-message list ("My Alerts",
 * unchanged — [MessageScanContent]), the full intercepted-message audit trail ("Message Log" —
 * [MessageVerificationContent]), and the region-wide daily fraud digest ("Regional Digest",
 * threshold-crossing clusters + cybercrime.gov.in scrape, targeted by the user's region/persona).
 * Lives at the app layer, not inside either feature module, since it composes two features
 * together — avoids a feature→feature dependency.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsHostScreen(navController: NavController) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val messageScanViewModel: MessageScanViewModel = hiltViewModel()
    val showAllMessages by messageScanViewModel.showAllMessages.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Alerts", fontWeight = FontWeight.Bold) },
                    actions = {
                        if (selectedTab == 0) {
                            IconButton(onClick = { messageScanViewModel.toggleFilter() }) {
                                Icon(
                                    imageVector = if (showAllMessages) Icons.Filled.FilterListOff else Icons.Filled.FilterList,
                                    contentDescription = if (showAllMessages) "Show Flagged Only" else "Show All Messages",
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("My Alerts") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Message Log") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Regional Digest") })
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background),
        ) {
            when (selectedTab) {
                0 -> MessageScanContent(navController = navController, viewModel = messageScanViewModel)
                1 -> MessageVerificationContent(navController = navController)
                2 -> RegionalDigestScreen()
            }
        }
    }
}
