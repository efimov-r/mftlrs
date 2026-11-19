/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import android.os.Parcelable
import android.text.TextUtils
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.ui.compose.MftlrsFastScrollbar

private val GRID_COLUMN_WIDTH = 180.dp // как FileListFragment.updateSpanCount: ширина / 180 dp, не меньше 2

// Состояние списка. Заполняет FileListFragment из своих наблюдателей LiveData, Compose только рисует
internal class FileListContentState {
    var files by mutableStateOf<List<FileItem>>(emptyList()) // уже отфильтрованы и отсортированы
    var isRefreshing by mutableStateOf(false) // индикатор свайпа: загрузка, когда файлы уже есть
    var isLoading by mutableStateOf(false) // индикатор по центру: загрузка без файлов
    var errorText by mutableStateOf<String?>(null)
    var isEmpty by mutableStateOf(false)
    var viewType by mutableStateOf(FileViewType.LIST)
    var selectedPaths by mutableStateOf<Set<Path>>(emptySet()) // снимок: FileItemSet меняется на месте
    var pickOptions by mutableStateOf<PickOptions?>(null)
    var nameEllipsize by mutableStateOf(TextUtils.TruncateAt.END)
    val gridState = LazyGridState()

    val scrollPosition: ScrollPosition
        get() = ScrollPosition(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)

    fun scrollTo(position: ScrollPosition) {
        gridState.requestScrollToItem(position.index, position.offset) // применится при следующем измерении
    }
}

// Позиция прокрутки для TrailData вместо состояния GridLayoutManager
@Parcelize
data class ScrollPosition(val index: Int, val offset: Int) : Parcelable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileListContent(
    state: FileListContentState,
    listener: FileItemListener,
    bottomInset: Dp, // под панелью навигации: список уходит под неё, последний элемент — над ней
    onRefresh: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columnCount = when (state.viewType) {
                    FileViewType.LIST -> 1
                    FileViewType.GRID -> (maxWidth / GRID_COLUMN_WIDTH).toInt().coerceAtLeast(2)
                }

                MftlrsFastScrollbar(
                    state = state.gridState,
                    columnCount = columnCount,
                    modifier = Modifier.fillMaxSize(),
                    bottomPadding = bottomInset,
                ) {
                    FileGrid(
                        state = state,
                        columnCount = columnCount,
                        bottomPadding = dimensionResource(R.dimen.list_bottom_padding_with_fab) + bottomInset,
                        listener = listener,
                    )
                }
            }
        }

        val statusModifier = Modifier
            .align(Alignment.Center)
            .padding(bottom = bottomInset)
        val errorText = state.errorText
        when {
            state.isLoading -> CircularProgressIndicator(modifier = statusModifier)
            errorText != null -> StatusMessage(R.drawable.error_icon_240dp, errorText, statusModifier)
            state.isEmpty -> StatusMessage(
                R.drawable.file_icon_240dp,
                stringResource(R.string.file_list_empty),
                statusModifier,
            )
        }
    }
}

@Composable
private fun FileGrid(
    state: FileListContentState,
    columnCount: Int,
    bottomPadding: Dp,
    listener: FileItemListener,
) {
    val hasSelection = state.selectedPaths.isNotEmpty()

    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        modifier = Modifier.fillMaxSize(),
        state = state.gridState,
        contentPadding = PaddingValues(bottom = bottomPadding),
    ) {
        items(state.files, key = { it.path.toString() }) { file ->
            val isChecked = file.path in state.selectedPaths
            when (state.viewType) {
                FileViewType.LIST -> FileListItem(
                    file = file,
                    isChecked = isChecked,
                    hasSelection = hasSelection,
                    pickOptions = state.pickOptions,
                    nameEllipsize = state.nameEllipsize,
                    listener = listener,
                )
                FileViewType.GRID -> FileGridItem(
                    file = file,
                    isChecked = isChecked,
                    hasSelection = hasSelection,
                    pickOptions = state.pickOptions,
                    listener = listener,
                )
            }
        }
    }
}

// Ошибка или «Пусто»: значок 240 dp и текст под ним
@Composable
private fun StatusMessage(
    @DrawableRes iconRes: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painter = painterResource(iconRes), contentDescription = null)
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
