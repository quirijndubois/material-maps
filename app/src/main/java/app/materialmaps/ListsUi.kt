@file:OptIn(ExperimentalMaterial3Api::class)

package app.materialmaps

import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

val PlaceList.icon: ImageVector get() = if (isFavourites) Icons.Filled.Star else Icons.AutoMirrored.Outlined.List

private fun placeCount(n: Int) = if (n == 1) "1 place" else "$n places"

/** Pick the lists a place is on, or start a new one with it. */
@Composable
fun SaveToListSheet(
    place: Place,
    lists: List<PlaceList>,
    onToggle: (PlaceList) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "Save “${place.name}”",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            lists.forEach { list ->
                val saved = list.places.any { it.isSameAs(place) }
                ListItem(
                    headlineContent = { Text(list.name) },
                    supportingContent = { Text(placeCount(list.places.size)) },
                    leadingContent = { Icon(list.icon, contentDescription = null) },
                    trailingContent = { Checkbox(checked = saved, onCheckedChange = { onToggle(list) }) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onToggle(list) },
                )
            }
            ListItem(
                headlineContent = { Text("New list") },
                leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { naming = true },
            )
        }
    }
    if (naming) {
        ListNameDialog(
            title = "New list",
            initial = "",
            onConfirm = {
                naming = false
                onCreate(it)
            },
            onDismiss = { naming = false },
        )
    }
}

@Composable
fun ListNameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** The saved lists, shown in the search panel before anything is typed. */
@Composable
fun SavedListsSection(lists: List<PlaceList>, onOpen: (PlaceList) -> Unit) {
    Text(
        "Saved",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
    lists.forEach { list ->
        ListItem(
            headlineContent = { Text(list.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(placeCount(list.places.size)) },
            leadingContent = { Icon(list.icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onOpen(list) },
        )
    }
}

/** An open list: its places, to pick one or take it off the list. */
@Composable
fun PlaceListPanel(
    list: PlaceList,
    here: Location?,
    onSelect: (Place) -> Unit,
    onRemove: (Place) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    ElevatedCard(modifier = modifier, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Column(Modifier.weight(1f)) {
                    Text(list.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        placeCount(list.places.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!list.isFavourites) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text("Delete list") }, onClick = { menu = false; onDelete() })
                        }
                    }
                }
            }
            if (list.places.isEmpty()) {
                Text(
                    "Nothing here yet. Open a place and tap Save to add it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = listHeight(360.dp))) {
                    items(list.places) { place ->
                        PlaceRow(place, here, onClick = { onSelect(place) }) {
                            IconButton(onClick = { onRemove(place) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove from list")
                            }
                        }
                    }
                }
            }
        }
    }
    if (renaming) {
        ListNameDialog(
            title = "Rename list",
            initial = list.name,
            onConfirm = {
                renaming = false
                onRename(it)
            },
            onDismiss = { renaming = false },
        )
    }
}

val QuickButton.icon: ImageVector
    get() = when (shortcut) {
        Shortcut.Home -> Icons.Outlined.Home
        Shortcut.Work -> Icons.Outlined.Work
        null -> category?.icon ?: Icons.Outlined.Home
    }

/** Home, Work and kinds of places, as chips under the search bar. */
@Composable
fun QuickChips(
    buttons: List<QuickButton>,
    shortcuts: Map<Shortcut, Place>,
    loading: Category?,
    onShortcut: (Shortcut) -> Unit,
    onCategory: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        buttons.forEach { button ->
            val shortcut = button.shortcut
            val category = button.category
            ElevatedAssistChip(
                onClick = { if (shortcut != null) onShortcut(shortcut) else if (category != null) onCategory(category) },
                label = {
                    Text(if (shortcut != null && shortcut !in shortcuts) "Set ${shortcut.label.lowercase()}" else button.label)
                },
                leadingIcon = {
                    if (category != null && loading == category) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(button.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
        }
    }
}
