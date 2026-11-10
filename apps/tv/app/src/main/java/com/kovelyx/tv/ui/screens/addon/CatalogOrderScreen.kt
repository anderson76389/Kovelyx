@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.kovelyx.tv.ui.screens.addon

import com.kovelyx.tv.ui.theme.KovelyxTheme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kovelyx.tv.ui.components.LoadingIndicator
import androidx.compose.ui.res.stringResource
import com.kovelyx.tv.R
import kotlinx.coroutines.launch

@Composable
fun CatalogOrderScreen(
    viewModel: CatalogOrderViewModel = hiltViewModel(),
    onBackPress: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    BackHandler { onBackPress() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = KovelyxTheme.spacing.xxxl, vertical = KovelyxTheme.spacing.xl)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = KovelyxTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(KovelyxTheme.spacing.lg)
        ) {
            item {
                Text(
                    text = stringResource(R.string.catalog_order_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = KovelyxTheme.colors.TextPrimary
                )
                Spacer(modifier = Modifier.height(KovelyxTheme.spacing.sm))
                Text(
                    text = stringResource(R.string.catalog_order_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KovelyxTheme.colors.TextSecondary
                )
                Spacer(modifier = Modifier.height(KovelyxTheme.spacing.lg))
                val followAddonsLabel = stringResource(R.string.catalog_order_follow_addons)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = followAddonsLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = KovelyxTheme.colors.TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.catalog_order_follow_addons_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = KovelyxTheme.colors.TextSecondary
                        )
                    }
                    Switch(
                        checked = uiState.followAddonsOrder,
                        onCheckedChange = { viewModel.toggleFollowAddonsOrder(it) },
                        modifier = Modifier.semantics { contentDescription = followAddonsLabel },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = KovelyxTheme.colors.Primary,
                            checkedTrackColor = KovelyxTheme.colors.Primary.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = KovelyxTheme.spacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    }
                }

                uiState.items.isEmpty() -> {
                    item {
                        Text(
                            text = stringResource(R.string.catalog_order_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = KovelyxTheme.colors.TextSecondary
                        )
                    }
                }

                else -> {
                    itemsIndexed(
                        items = uiState.items,
                        key = { _, item -> item.key }
                    ) { index, item ->
                        CatalogOrderCard(
                            item = item,
                            onMoveUp = {
                                viewModel.moveUp(item.key)
                                scope.launch {
                                    listState.animateScrollToItem((index - 1).coerceAtLeast(0))
                                }
                            },
                            onMoveDown = {
                                viewModel.moveDown(item.key)
                                scope.launch {
                                    listState.animateScrollToItem(
                                        (index + 1).coerceAtMost(uiState.items.lastIndex)
                                    )
                                }
                            },
                            onToggleEnabled = { viewModel.toggleCatalogEnabled(item.disableKey) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogOrderCard(
    item: CatalogOrderItem,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KovelyxTheme.colors.BackgroundCard),
        shape = RoundedCornerShape(KovelyxTheme.radii.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${item.catalogName} - ${item.typeLabel.toDisplayTypeLabel()}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (item.isDisabled) KovelyxTheme.colors.TextSecondary else KovelyxTheme.colors.TextPrimary
                )
                Spacer(modifier = Modifier.height(KovelyxTheme.spacing.xs))
                Text(
                    text = item.addonName,
                    style = MaterialTheme.typography.bodySmall,
                    color = KovelyxTheme.colors.TextSecondary
                )
                if (item.isDisabled) {
                    Spacer(modifier = Modifier.height(KovelyxTheme.spacing.xs))
                    Text(
                        text = stringResource(R.string.catalog_order_disabled_on_home),
                        style = MaterialTheme.typography.bodySmall,
                        color = KovelyxTheme.colors.Error
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(KovelyxTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onMoveUp,
                    enabled = item.canMoveUp,
                    colors = ButtonDefaults.colors(
                        containerColor = KovelyxTheme.colors.BackgroundCard,
                        contentColor = KovelyxTheme.colors.TextSecondary,
                        focusedContainerColor = KovelyxTheme.colors.FocusBackground,
                        focusedContentColor = KovelyxTheme.colors.Primary
                    ),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(
                            border = KovelyxTheme.focusRing.border(KovelyxTheme.spacing.xxs),
                            shape = RoundedCornerShape(KovelyxTheme.radii.md)
                        )
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(KovelyxTheme.radii.md))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = stringResource(R.string.cd_move_up)
                    )
                }

                Button(
                    onClick = onMoveDown,
                    enabled = item.canMoveDown,
                    colors = ButtonDefaults.colors(
                        containerColor = KovelyxTheme.colors.BackgroundCard,
                        contentColor = KovelyxTheme.colors.TextSecondary,
                        focusedContainerColor = KovelyxTheme.colors.FocusBackground,
                        focusedContentColor = KovelyxTheme.colors.Primary
                    ),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(
                            border = KovelyxTheme.focusRing.border(KovelyxTheme.spacing.xxs),
                            shape = RoundedCornerShape(KovelyxTheme.radii.md)
                        )
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(KovelyxTheme.radii.md))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = stringResource(R.string.cd_move_down)
                    )
                }

                Button(
                    onClick = onToggleEnabled,
                    colors = ButtonDefaults.colors(
                        containerColor = KovelyxTheme.colors.BackgroundCard,
                        contentColor = if (item.isDisabled) KovelyxTheme.colors.Success else KovelyxTheme.colors.TextSecondary,
                        focusedContainerColor = KovelyxTheme.colors.FocusBackground,
                        focusedContentColor = if (item.isDisabled) KovelyxTheme.colors.Success else KovelyxTheme.colors.Error
                    ),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(
                            border = KovelyxTheme.focusRing.border(KovelyxTheme.spacing.xxs),
                            shape = RoundedCornerShape(KovelyxTheme.radii.md)
                        )
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(KovelyxTheme.radii.md))
                ) {
                    Text(text = if (item.isDisabled) stringResource(R.string.catalog_order_enable) else stringResource(R.string.catalog_order_disable))
                }
            }
        }
    }
}

private fun String.toDisplayTypeLabel(): String {
    return replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase() else ch.toString()
    }
}
