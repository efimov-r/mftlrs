/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.fileproperties.permission

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compat.ListFormatterCompat
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.fileproperties.FilePropertyItem
import me.zhanghai.android.files.fileproperties.modeBitNames
import me.zhanghai.android.files.provider.common.PosixFileAttributes
import me.zhanghai.android.files.provider.common.PosixFileModeBit
import me.zhanghai.android.files.provider.common.PosixGroup
import me.zhanghai.android.files.provider.common.PosixUser
import me.zhanghai.android.files.provider.common.toByteString
import me.zhanghai.android.files.ui.compose.MftlrsDialog
import me.zhanghai.android.files.ui.compose.MftlrsViewModelScope
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.valueCompat

// Диалоги вкладки «Права», были SetOwnerDialogFragment, SetGroupDialogFragment, SetModeDialogFragment,
// SetSeLinuxContextDialogFragment. ОК запускает задачу FileJobService, если что-то изменилось или
// отмечено «Рекурсивно»

internal enum class PrincipalType(@StringRes val titleRes: Int, @DrawableRes val iconRes: Int) {
    OWNER(R.string.file_properties_permission_set_owner_title, R.drawable.person_icon_control_normal_24dp),
    GROUP(R.string.file_properties_permission_set_group_title, R.drawable.people_icon_control_normal_24dp),
}

// Владелец или группа: поиск по списку пользователей (групп) и приложений, выбор одного
@Composable
internal fun SetPrincipalDialog(file: FileItem, type: PrincipalType, onDismissRequest: () -> Unit) {
    MftlrsViewModelScope("SetPrincipalDialog:$type") { finish ->
        val dismiss = {
            finish()
            onDismissRequest()
        }
        val viewModel: SetPrincipalViewModel = viewModel {
            when (type) {
                PrincipalType.OWNER -> SetOwnerViewModel()
                PrincipalType.GROUP -> SetGroupViewModel()
            }
        }
        val attributes = file.attributes as PosixFileAttributes
        val filePrincipalId = when (type) {
            PrincipalType.OWNER -> attributes.owner()!!.id
            PrincipalType.GROUP -> attributes.group()!!.id
        }
        // Первый показ — выбран текущий и список прокручивается к нему
        val isFirstShow = remember(viewModel) {
            val isFirst = viewModel.selectionLiveData.value == null
            if (isFirst) viewModel.selectionLiveData.value = filePrincipalId
            isFirst
        }
        var isScrollPending by rememberSaveable { mutableStateOf(isFirstShow) }
        val selectedId by viewModel.selectionLiveData.observeAsState()
        val stateful by viewModel.filteredPrincipalListLiveData.observeAsState()
        val filterState = rememberTextFieldState(viewModel.filter)
        var isRecursive by rememberSaveable { mutableStateOf(false) }
        val listState = rememberLazyListState()
        val context = LocalContext.current

        LaunchedEffect(filterState) {
            snapshotFlow { filterState.text.toString() }.collect { viewModel.filter = it }
        }
        LaunchedEffect(stateful) {
            val principals = (stateful as? Success)?.value ?: return@LaunchedEffect
            if (!isScrollPending) return@LaunchedEffect
            val index = principals.indexOfFirst { it.id == selectedId }
            if (index != -1) listState.scrollToItem(index)
            isScrollPending = false
        }

        MftlrsDialog(
            title = stringResource(type.titleRes),
            onDismissRequest = dismiss,
            buttons = {
                TextButton(onClick = dismiss) { Text(stringResource(android.R.string.cancel)) }
                TextButton(
                    onClick = {
                        dismiss()
                        val id = viewModel.selectionLiveData.valueCompat
                        if (!isRecursive && id == filePrincipalId) return@TextButton
                        val principals = (viewModel.principalListStateful as? Success)?.value ?: return@TextButton
                        val principal = principals.find { it.id == id } ?: return@TextButton
                        val name = principal.name?.toByteString()
                        when (type) {
                            PrincipalType.OWNER ->
                                FileJobService.setOwner(file.path, PosixUser(principal.id, name), isRecursive, context)
                            PrincipalType.GROUP ->
                                FileJobService.setGroup(file.path, PosixGroup(principal.id, name), isRecursive, context)
                        }
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
        ) {
            TextField(
                state = filterState,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.file_properties_permission_set_principal_filter_hint)) },
                leadingIcon = { Icon(painterResource(R.drawable.filter_icon_control_normal_24dp), null) },
                lineLimits = TextFieldLineLimits.SingleLine,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = 64.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (val stateful = stateful) {
                    is Success ->
                        if (stateful.value.isEmpty()) {
                            Text(stringResource(R.string.empty), style = MaterialTheme.typography.bodyLarge)
                        } else {
                            LazyColumn(state = listState) {
                                items(stateful.value) { principal ->
                                    PrincipalItemRow(principal, type, principal.id == selectedId) {
                                        viewModel.selectionLiveData.value = principal.id
                                    }
                                }
                            }
                        }
                    is Failure ->
                        Text(
                            text = stateful.throwable.toString(),
                            modifier = Modifier.padding(horizontal = 24.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    is Loading, null -> CircularProgressIndicator()
                }
            }
            if (file.attributes.isDirectory) {
                RecursiveCheckbox(isRecursive) { isRecursive = it }
            }
        }
    }
}

@Composable
private fun PrincipalItemRow(principal: PrincipalItem, type: PrincipalType, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconPainter = painterResource(type.iconRes)
        val iconModifier = Modifier.size(dimensionResource(R.dimen.icon_size))
        val applicationInfo = principal.applicationInfos.firstOrNull()
        if (applicationInfo != null) {
            AsyncImage(
                model = applicationInfo,
                contentDescription = null,
                imageLoader = LocalContext.current.imageLoader,
                modifier = iconModifier,
                placeholder = iconPainter,
                error = iconPainter,
            )
        } else {
            Icon(iconPainter, null, iconModifier, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
        ) {
            val name = principal.name
            Text(
                text = if (name != null) {
                    stringResource(R.string.file_properties_permission_principal_format, name, principal.id)
                } else {
                    principal.id.toString()
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = principal.applicationLabels.firstOrNull()
                    ?: stringResource(R.string.file_properties_permission_set_principal_system),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        RadioButton(selected = isSelected, onClick = null)
    }
}

// Права rwx: владелец, группа, остальные и особые биты — каждый набор флажками в выпадающем списке
@Composable
internal fun SetModeDialog(file: FileItem, onDismissRequest: () -> Unit) {
    MftlrsViewModelScope("SetModeDialog") { finish ->
        val dismiss = {
            finish()
            onDismissRequest()
        }
        val fileMode = (file.attributes as PosixFileAttributes).mode()!!
        val viewModel = viewModel { SetModeViewModel(fileMode) }
        val mode by viewModel.modeLiveData.observeAsState(fileMode)
        val isDirectory = file.attributes.isDirectory
        val normalNames = stringArrayResource(
            if (isDirectory) {
                R.array.file_properties_permission_set_mode_normal_mode_bits_directory
            } else {
                R.array.file_properties_permission_set_mode_normal_mode_bits_file
            }
        )
        val specialNames = stringArrayResource(R.array.file_properties_permission_set_mode_special_mode_bits)
        var isRecursive by rememberSaveable { mutableStateOf(false) }
        var isUppercaseX by rememberSaveable { mutableStateOf(true) }
        val context = LocalContext.current

        MftlrsDialog(
            title = stringResource(R.string.file_properties_permission_set_mode_title),
            onDismissRequest = dismiss,
            buttons = {
                TextButton(onClick = dismiss) { Text(stringResource(android.R.string.cancel)) }
                TextButton(
                    onClick = {
                        dismiss()
                        val newMode = viewModel.mode
                        if (!isRecursive && newMode == fileMode) return@TextButton
                        FileJobService.setMode(file.path, newMode, isRecursive, isUppercaseX, context)
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeBitsField(R.string.file_properties_permission_owner, OWNER_MODE_BITS, normalNames, mode) {
                    viewModel.toggleModeBit(it)
                }
                ModeBitsField(R.string.file_properties_permission_group, GROUP_MODE_BITS, normalNames, mode) {
                    viewModel.toggleModeBit(it)
                }
                ModeBitsField(R.string.file_properties_permission_set_mode_others, OTHERS_MODE_BITS, normalNames, mode) {
                    viewModel.toggleModeBit(it)
                }
                ModeBitsField(R.string.file_properties_permission_set_mode_special, SPECIAL_MODE_BITS, specialNames, mode) {
                    viewModel.toggleModeBit(it)
                }
                if (isDirectory) {
                    Column {
                        RecursiveCheckbox(isRecursive, horizontalPadding = 0.dp) { isRecursive = it }
                        CheckboxRow(
                            text = stringResource(R.string.file_properties_permission_set_mode_uppercase_x),
                            isChecked = isUppercaseX,
                            isEnabled = isRecursive,
                            horizontalPadding = 0.dp,
                        ) { isUppercaseX = it }
                    }
                }
            }
        }
    }
}

// Набор из трёх битов: строка с их названиями, по нажатию — флажки; список не закрывается после выбора
@Composable
private fun ModeBitsField(
    @StringRes labelRes: Int,
    bits: List<PosixFileModeBit>,
    names: Array<String>,
    mode: Set<PosixFileModeBit>,
    onToggle: (PosixFileModeBit) -> Unit,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val checkedNames = modeBitNames(mode, bits, names)

    Box {
        FilePropertyItem(
            label = stringResource(labelRes),
            text = if (checkedNames.isEmpty()) stringResource(R.string.none) else ListFormatterCompat.format(checkedNames),
        ) { isExpanded = true }
        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            bits.forEachIndexed { index, bit ->
                DropdownMenuItem(
                    text = { Text(names[index]) },
                    onClick = { onToggle(bit) },
                    leadingIcon = { Checkbox(checked = bit in mode, onCheckedChange = null) },
                )
            }
        }
    }
}

// Контекст SELinux: поле в фокусе с клавиатурой; «Восстановить» — restorecon
@Composable
internal fun SetSeLinuxContextDialog(file: FileItem, onDismissRequest: () -> Unit) {
    val fileContext = (file.attributes as PosixFileAttributes).seLinuxContext()?.toString().orEmpty()
    val state = rememberTextFieldState(fileContext)
    var isRecursive by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    MftlrsDialog(
        title = stringResource(R.string.file_properties_permission_set_selinux_context_title),
        onDismissRequest = onDismissRequest,
        buttons = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    FileJobService.restoreSeLinuxContext(file.path, isRecursive, context)
                },
            ) { Text(stringResource(R.string.file_properties_permission_set_selinux_context_restore)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismissRequest) { Text(stringResource(android.R.string.cancel)) }
            TextButton(
                onClick = {
                    onDismissRequest()
                    val seLinuxContext = state.text.toString()
                    if (!isRecursive && seLinuxContext == fileContext) return@TextButton
                    FileJobService.setSeLinuxContext(file.path, seLinuxContext, isRecursive, context)
                },
            ) { Text(stringResource(android.R.string.ok)) }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .focusRequester(focusRequester),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            if (file.attributes.isDirectory) {
                RecursiveCheckbox(isRecursive) { isRecursive = it }
            }
        }
    }
}

@Composable
private fun RecursiveCheckbox(
    isChecked: Boolean,
    horizontalPadding: Dp = 24.dp,
    onCheckedChange: (Boolean) -> Unit,
) {
    CheckboxRow(
        text = stringResource(R.string.file_properties_permission_recursive),
        isChecked = isChecked,
        horizontalPadding = horizontalPadding,
        onCheckedChange = onCheckedChange,
    )
}

// Флажок с подписью, нажимается вся строка
@Composable
private fun CheckboxRow(
    text: String,
    isChecked: Boolean,
    isEnabled: Boolean = true,
    horizontalPadding: Dp,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = isChecked, enabled = isEnabled, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = isChecked, onCheckedChange = null, enabled = isEnabled)
        Text(
            text = text,
            modifier = Modifier.padding(start = 16.dp),
            color = if (isEnabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private val OWNER_MODE_BITS = listOf(
    PosixFileModeBit.OWNER_READ,
    PosixFileModeBit.OWNER_WRITE,
    PosixFileModeBit.OWNER_EXECUTE,
)
private val GROUP_MODE_BITS = listOf(
    PosixFileModeBit.GROUP_READ,
    PosixFileModeBit.GROUP_WRITE,
    PosixFileModeBit.GROUP_EXECUTE,
)
private val OTHERS_MODE_BITS = listOf(
    PosixFileModeBit.OTHERS_READ,
    PosixFileModeBit.OTHERS_WRITE,
    PosixFileModeBit.OTHERS_EXECUTE,
)
private val SPECIAL_MODE_BITS = listOf(
    PosixFileModeBit.SET_USER_ID,
    PosixFileModeBit.SET_GROUP_ID,
    PosixFileModeBit.STICKY,
)
