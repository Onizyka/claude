package ru.inventory.dc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import ru.inventory.dc.data.DATA_CENTERS
import ru.inventory.dc.data.Placement
import ru.inventory.dc.data.hallLabel
import ru.inventory.dc.ui.theme.BrandColors

/** Экран «Где устанавливается оборудование?» — заполняется один раз для серии устройств. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlacementScreen(
    initial: Placement,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onDone: (Placement) -> Unit,
) {
    val knownNames = DATA_CENTERS.map { it.name }
    var site by rememberSaveable { mutableStateOf(initial.site) }
    var hall by rememberSaveable { mutableStateOf(initial.hall) }
    var rack by rememberSaveable { mutableStateOf(initial.rack) }
    var customSite by rememberSaveable { mutableStateOf(initial.site.isNotBlank() && initial.site !in knownNames) }

    val dataCenter = DATA_CENTERS.firstOrNull { it.name == site && !customSite }
    val halls = dataCenter?.halls.orEmpty()
    var customHall by rememberSaveable(site) { mutableStateOf(hall.isNotBlank() && hall !in halls) }

    Scaffold(
        topBar = {
            BrandTopBar(
                title = "Место установки",
                subtitle = "Задаётся один раз для серии устройств",
                navigationIcon = if (canGoBack) {
                    {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                } else null,
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Button(
                    onClick = { onDone(Placement(site = site, hall = hall, rack = rack)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Продолжить")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Где будет стоять оборудование?",
                style = MaterialTheme.typography.headlineSmall,
                color = BrandColors.DarkBlue,
            )
            Text(
                "ЦОД и машзал подставятся во все следующие записи. Изменить их можно в любой момент кнопкой «Изменить».",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard("ЦОД", Icons.Filled.Apartment) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DATA_CENTERS.forEach { dc ->
                        ChoiceCard(
                            title = dc.name,
                            subtitle = if (dc.halls.isNotEmpty()) "Машзалы ${dc.halls.joinToString(", ")}" else null,
                            selected = !customSite && site == dc.name,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                customSite = false
                                if (site != dc.name) {
                                    site = dc.name
                                    hall = ""
                                }
                            },
                        )
                    }
                }
                FilterChip(
                    selected = customSite,
                    onClick = {
                        customSite = !customSite
                        site = ""
                        hall = ""
                    },
                    label = { Text("Другой ЦОД") },
                )
                if (customSite) {
                    Field(site, { site = it }, label = "Название ЦОД", capitalization = KeyboardCapitalization.Sentences)
                }
            }

            SectionCard("Машзал", Icons.Filled.MeetingRoom) {
                if (halls.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        halls.forEach { h ->
                            ChoiceCard(
                                title = h,
                                subtitle = "машзал",
                                selected = !customHall && hall == h,
                                modifier = Modifier.size(width = 76.dp, height = 72.dp),
                                onClick = {
                                    customHall = false
                                    hall = h
                                },
                            )
                        }
                    }
                    FilterChip(
                        selected = customHall,
                        onClick = {
                            customHall = !customHall
                            hall = ""
                        },
                        label = { Text("Другой") },
                    )
                }
                if (halls.isEmpty() || customHall) {
                    Field(hall, { hall = it }, label = "Номер или название машзала")
                }
            }

            SectionCard("Стойка", Icons.Filled.Dns) {
                Field(
                    rack, { rack = it },
                    label = "Стойка (необязательно)",
                    supportingText = "Если всё оборудование серии ставится в одну стойку",
                    capitalization = KeyboardCapitalization.Characters,
                )
            }

            val summary = listOf(site, hallLabel(hall), rack.takeIf { it.isNotBlank() }?.let { "Стойка $it" }.orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (summary.isNotBlank()) {
                InfoBanner(text = summary)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceCard(
    title: String,
    subtitle: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else BrandColors.DarkBlue,
        ),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                }
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
