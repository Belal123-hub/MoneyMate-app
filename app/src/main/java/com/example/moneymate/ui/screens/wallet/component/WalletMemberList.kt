package com.example.moneymate.ui.screens.wallet.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.wallet.model.WalletMember

@Composable
fun WalletMemberList(
    members: List<WalletMember>,
    currentUserRole: String?,
    canManage: Boolean,
    onRoleChange: (userId: Int, newRole: String) -> Unit,
    onRemoveMember: (userId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        members.forEach { member ->
            WalletMemberRow(
                member = member,
                canManage = canManage,
                onRoleChange = onRoleChange,
                onRemoveMember = onRemoveMember
            )
        }
        if (members.isEmpty()) {
            Text(
                text = "No members yet",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun WalletMemberRow(
    member: WalletMember,
    canManage: Boolean,
    onRoleChange: (userId: Int, newRole: String) -> Unit,
    onRemoveMember: (userId: Int) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF5F5F5),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.userName.ifBlank { member.userEmail },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = member.userEmail,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            RoleBadge(role = member.role)
            if (canManage) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Member actions")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (member.role.lowercase() != "admin") {
                        DropdownMenuItem(
                            text = { Text("Make editor") },
                            onClick = {
                                menuExpanded = false
                                onRoleChange(member.userId, "editor")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Make viewer") },
                            onClick = {
                                menuExpanded = false
                                onRoleChange(member.userId, "viewer")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            onClick = {
                                menuExpanded = false
                                onRemoveMember(member.userId)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoleBadge(
    role: String,
    modifier: Modifier = Modifier,
    /** Use on wallet cards so label stays readable on any wallet color. */
    onColoredBackground: Boolean = false,
    contentColor: Color? = null
) {
    val label = when (role.lowercase()) {
        "admin" -> "Admin"
        "editor" -> "Editor"
        "viewer" -> "Viewer"
        else -> role.replaceFirstChar { it.uppercase() }
    }
    val accent = when (role.lowercase()) {
        "admin" -> Color(0xFFEF4444)
        "editor" -> Color(0xFF22C55E)
        "viewer" -> Color(0xFFE5E7EB)
        else -> Color(0xFFE5E7EB)
    }
    val fg = contentColor ?: if (onColoredBackground) Color.White else accent
    val surfaceColor = if (onColoredBackground) {
        Color.Black.copy(alpha = 0.32f)
    } else {
        accent.copy(alpha = 0.15f)
    }
    Surface(
        modifier = modifier,
        color = surfaceColor,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = fg
        )
    }
}
