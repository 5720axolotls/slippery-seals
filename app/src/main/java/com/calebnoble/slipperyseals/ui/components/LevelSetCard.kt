package com.calebnoble.slipperyseals.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calebnoble.slipperyseals.ui.theme.CompletedGreen
import com.calebnoble.slipperyseals.ui.theme.StarGoldText

@Composable
fun LevelSetCard(
    name: String,
    total: Int,
    completed: Int,
    starred: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("$total levels", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (completed > 0) {
                    Text(
                        "$completed/$total completed",
                        fontSize = 14.sp,
                        color = CompletedGreen
                    )
                }
                if (starred > 0) {
                    Text(
                        "$starred starred",
                        fontSize = 14.sp,
                        color = StarGoldText
                    )
                }
            }
        }
    }
}
