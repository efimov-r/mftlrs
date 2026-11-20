/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 * Modified for mftlrs, 2026.
 */

package me.zhanghai.android.files.filelist

import android.os.Parcelable
import android.text.TextUtils
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import java8.nio.file.Path
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.filelist.FileSortOptions.By
import me.zhanghai.android.files.filelist.FileSortOptions.Order
import me.zhanghai.android.files.provider.archive.archiveRefresh
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.CloseableLiveData
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.valueCompat
import java.io.Closeable

// TODO: Use SavedStateHandle to save state.
class FileListViewModel : ViewModel() {
    private val trailLiveData = TrailLiveData()
    val hasTrail: Boolean
        get() = trailLiveData.value != null
    val pendingState: Parcelable?
        get() = trailLiveData.valueCompat.pendingState

    fun navigateTo(lastState: Parcelable, path: Path) = trailLiveData.navigateTo(lastState, path)

    fun resetTo(path: Path) = trailLiveData.resetTo(path)

    fun navigateUp(): Boolean = trailLiveData.navigateUp()

    val currentPathLiveData = trailLiveData.map { it.currentPath }
    val currentPath: Path
        get() = currentPathLiveData.valueCompat

    private val _searchStateLiveData = MutableLiveData(SearchState(false, ""))
    val searchStateLiveData: LiveData<SearchState> = _searchStateLiveData
    val searchState: SearchState
        get() = _searchStateLiveData.valueCompat

    fun search(query: String) {
        val searchState = _searchStateLiveData.valueCompat
        if (searchState.isSearching && searchState.query == query) {
            return
        }
        _searchStateLiveData.value = SearchState(true, query)
    }

    fun stopSearching() {
        val searchState = _searchStateLiveData.valueCompat
        if (!searchState.isSearching) {
            return
        }
        _searchStateLiveData.value = SearchState(false, "")
    }

    private val _fileListLiveData =
        FileListSwitchMapLiveData(currentPathLiveData, _searchStateLiveData)
    val fileListLiveData: LiveData<Stateful<List<FileItem>>>
        get() = _fileListLiveData
    val fileListStateful: Stateful<List<FileItem>>
        get() = _fileListLiveData.valueCompat

    fun reload() {
        val path = currentPath
        if (path.isArchivePath) {
            path.archiveRefresh()
        }
        _fileListLiveData.reload()
    }

    val searchViewExpandedLiveData = MutableLiveData(false)
    var isSearchViewExpanded: Boolean
        get() = searchViewExpandedLiveData.valueCompat
        set(value) {
            if (searchViewExpandedLiveData.valueCompat == value) {
                return
            }
            searchViewExpandedLiveData.value = value
        }

    private val _searchViewQueryLiveData = MutableLiveData("")
    var searchViewQuery: String
        get() = _searchViewQueryLiveData.valueCompat
        set(value) {
            if (_searchViewQueryLiveData.valueCompat == value) {
                return
            }
            _searchViewQueryLiveData.value = value
        }

    val breadcrumbLiveData: LiveData<BreadcrumbData> = BreadcrumbLiveData(trailLiveData)
    val canNavigateUpBreadcrumb: Boolean
        get() = breadcrumbLiveData.valueCompat.selectedIndex > 0

    private val _viewTypeLiveData = FileViewTypeLiveData(currentPathLiveData)
    val viewTypeLiveData: LiveData<FileViewType> = _viewTypeLiveData
    var viewType: FileViewType
        get() = _viewTypeLiveData.valueCompat
        set(value) {
            _viewTypeLiveData.putValue(value)
        }

    private val _sortOptionsLiveData = FileSortOptionsLiveData(currentPathLiveData)
    val sortOptionsLiveData: LiveData<FileSortOptions> = _sortOptionsLiveData
    val sortOptions: FileSortOptions
        get() = _sortOptionsLiveData.valueCompat

    fun setSortBy(by: By) = _sortOptionsLiveData.putBy(by)

    fun setSortOrder(order: Order) = _sortOptionsLiveData.putOrder(order)

    fun setSortDirectoriesFirst(isDirectoriesFirst: Boolean) =
        _sortOptionsLiveData.putIsDirectoriesFirst(isDirectoriesFirst)

    private val _viewSortPathSpecificLiveData =
        FileViewSortPathSpecificLiveData(currentPathLiveData)
    val viewSortPathSpecificLiveData: LiveData<Boolean>
        get() = _viewSortPathSpecificLiveData
    var isViewSortPathSpecific: Boolean
        get() = _viewSortPathSpecificLiveData.valueCompat
        set(value) {
            _viewSortPathSpecificLiveData.putValue(value)
        }

    private val _pickOptionsLiveData = MutableLiveData<PickOptions?>()
    val pickOptionsLiveData: LiveData<PickOptions?>
        get() = _pickOptionsLiveData
    var pickOptions: PickOptions?
        get() = _pickOptionsLiveData.value
        set(value) {
            _pickOptionsLiveData.value = value
        }

    var isCreateFileNameEditInitialized: Boolean = false

    private val _selectedFilesLiveData = MutableLiveData(fileItemSetOf())
    val selectedFilesLiveData: LiveData<FileItemSet>
        get() = _selectedFilesLiveData
    val selectedFiles: FileItemSet
        get() = _selectedFilesLiveData.valueCompat

    fun selectFile(file: FileItem, selected: Boolean) {
        selectFiles(fileItemSetOf(file), selected)
    }

    fun selectFiles(files: FileItemSet, selected: Boolean) {
        val selectedFiles = _selectedFilesLiveData.valueCompat
        if (selectedFiles === files) {
            if (!selected && selectedFiles.isNotEmpty()) {
                selectedFiles.clear()
                _selectedFilesLiveData.value = selectedFiles
            }
            return
        }
        var changed = false
        for (file in files) {
            changed = changed or if (selected) {
                selectedFiles.add(file)
            } else {
                selectedFiles.remove(file)
            }
        }
        if (changed) {
            _selectedFilesLiveData.value = selectedFiles
        }
    }

    fun replaceSelectedFiles(files: FileItemSet) {
        val selectedFiles = _selectedFilesLiveData.valueCompat
        if (selectedFiles == files) {
            return
        }
        selectedFiles.clear()
        selectedFiles.addAll(files)
        _selectedFilesLiveData.value = selectedFiles
    }

    fun clearSelectedFiles() {
        val selectedFiles = _selectedFilesLiveData.valueCompat
        if (selectedFiles.isEmpty()) {
            return
        }
        selectedFiles.clear()
        _selectedFilesLiveData.value = selectedFiles
    }

    val pasteStateLiveData: LiveData<PasteState> = _pasteStateLiveData
    val pasteState: PasteState
        get() = _pasteStateLiveData.valueCompat

    fun addToPasteState(copy: Boolean, files: FileItemSet) {
        val pasteState = _pasteStateLiveData.valueCompat
        var changed = false
        if (pasteState.copy != copy) {
            changed = pasteState.files.isNotEmpty()
            pasteState.files.clear()
            pasteState.copy = copy
        }
        changed = changed or pasteState.files.addAll(files)
        if (changed) {
            _pasteStateLiveData.value = pasteState
        }
    }

    fun clearPasteState() {
        val pasteState = _pasteStateLiveData.valueCompat
        if (pasteState.files.isEmpty()) {
            return
        }
        pasteState.files.clear()
        _pasteStateLiveData.value = pasteState
    }

    private val _isRequestingStorageAccessLiveData = MutableLiveData(false)
    var isStorageAccessRequested: Boolean
        get() = _isRequestingStorageAccessLiveData.valueCompat
        set(value) {
            _isRequestingStorageAccessLiveData.value = value
        }

    private val _isRequestingNotificationPermissionLiveData = MutableLiveData(false)
    var isNotificationPermissionRequested: Boolean
        get() = _isRequestingNotificationPermissionLiveData.valueCompat
        set(value) {
            _isRequestingNotificationPermissionLiveData.value = value
        }

    // mftlrs: the screen state for Compose, computed here instead of in the observers of
    // FileListFragment. No strings: Compose builds the text. The live data above are observed only
    // while the screen collects the state, otherwise FileListLiveData would reload in background.
    private val _uiState = MutableStateFlow(FileListUiState())
    internal val uiState: StateFlow<FileListUiState> = flow {
        coroutineScope {
            launch { currentPathLiveData.asFlow().collect { onCurrentPathChanged() } }
            launch { searchViewExpandedLiveData.asFlow().collect { onSearchViewExpandedChanged(it) } }
            launch { _searchViewQueryLiveData.asFlow().collect { onSearchViewQueryChanged(it) } }
            launch { viewTypeLiveData.asFlow().collect { onViewTypeChanged(it) } }
            launch { sortOptionsLiveData.asFlow().collect { onSortOptionsChanged(it) } }
            launch {
                viewSortPathSpecificLiveData.asFlow().collect { onViewSortPathSpecificChanged(it) }
            }
            launch { pickOptionsLiveData.asFlow().collect { onPickOptionsChanged(it) } }
            launch { selectedFilesLiveData.asFlow().collect { onSelectedFilesChanged(it) } }
            launch { pasteStateLiveData.asFlow().collect { updateBottomBar() } }
            launch { Settings.FILE_NAME_ELLIPSIZE.asFlow().collect { onFileNameEllipsizeChanged(it) } }
            launch { fileListLiveData.asFlow().collect { onFileListChanged(it) } }
            launch {
                Settings.FILE_LIST_SHOW_HIDDEN_FILES.asFlow().collect { onShowHiddenFilesChanged(it) }
            }
            emitAll(_uiState)
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value)

    // mftlrs: one-time events for FileListFragment.
    private val _events = Channel<FileListEvent>(Channel.BUFFERED)
    internal val events: Flow<FileListEvent> = _events.receiveAsFlow()

    private var lastIsSearching = false

    fun setFabExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isFabExpanded = expanded) }
    }

    fun setNavigationOpen(open: Boolean) {
        _uiState.update { it.copy(isNavigationOpen = open) }
    }

    private fun onCurrentPathChanged() {
        updateSelectionBar()
        updateBottomBar()
    }

    private fun onSearchViewExpandedChanged(expanded: Boolean) {
        _uiState.update { it.copy(topBar = it.topBar.copy(isSearchExpanded = expanded)) }
    }

    private fun onSearchViewQueryChanged(query: String) {
        _uiState.update { it.copy(topBar = it.topBar.copy(searchQuery = query)) }
    }

    private fun onViewTypeChanged(viewType: FileViewType) {
        _uiState.update {
            it.copy(
                content = it.content.copy(viewType = viewType),
                topBar = it.topBar.copy(viewType = viewType),
            )
        }
    }

    private fun onSortOptionsChanged(sortOptions: FileSortOptions) {
        _uiState.update { it.copy(topBar = it.topBar.copy(sortOptions = sortOptions)) }
        updateFiles()
    }

    private fun onViewSortPathSpecificChanged(pathSpecific: Boolean) {
        _uiState.update { it.copy(topBar = it.topBar.copy(isViewSortPathSpecific = pathSpecific)) }
    }

    private fun onShowHiddenFilesChanged(showHiddenFiles: Boolean) {
        _uiState.update { it.copy(topBar = it.topBar.copy(showHiddenFiles = showHiddenFiles)) }
        updateFiles()
    }

    private fun onFileNameEllipsizeChanged(fileNameEllipsize: TextUtils.TruncateAt) {
        _uiState.update { it.copy(content = it.content.copy(nameEllipsize = fileNameEllipsize)) }
    }

    private fun onFileListChanged(stateful: Stateful<List<FileItem>>) {
        val files = stateful.value
        val isSearching = searchState.isSearching
        val subtitle = when {
            stateful is Failure -> FileListUiState.Subtitle.Error
            stateful is Loading && !isSearching -> FileListUiState.Subtitle.Loading
            else -> {
                val allFiles = files.orEmpty()
                val directoryCount = allFiles.count { it.attributes.isDirectory }
                FileListUiState.Subtitle.Counts(directoryCount, allFiles.size - directoryCount)
            }
        }
        val hasFiles = !files.isNullOrEmpty()
        var errorText: String? = null
        val throwable = (stateful as? Failure)?.throwable
        if (throwable != null) {
            throwable.printStackTrace()
            val error = throwable.toString()
            if (hasFiles) {
                _events.trySend(FileListEvent.ShowError(error))
            } else {
                errorText = error
            }
        }
        _uiState.update {
            it.copy(
                content = it.content.copy(
                    isRefreshing = stateful is Loading && (hasFiles || isSearching),
                    isLoading = stateful is Loading && !(hasFiles || isSearching),
                    errorText = errorText,
                    isEmpty = stateful is Success && !hasFiles,
                ),
                topBar = it.topBar.copy(subtitle = subtitle),
            )
        }
        if (files != null) {
            updateFiles()
        } else {
            // A new directory starts from the top, as the cleared RecyclerView did.
            _uiState.update { it.copy(content = it.content.copy(files = emptyList())) }
            _events.trySend(FileListEvent.ScrollTo(ScrollPosition(0, 0)))
        }
        if (stateful is Success) {
            (pendingState as? ScrollPosition)?.let { _events.trySend(FileListEvent.ScrollTo(it)) }
        }
    }

    // Was FileListFragment.updateAdapterFileList(), and sorting in the removed FileListAdapter.
    private fun updateFiles() {
        // May be called by sort options before the file list has a value.
        var files = _fileListLiveData.value?.value ?: return
        val topBar = _uiState.value.topBar
        if (!topBar.showHiddenFiles) {
            files = files.filterNot { it.isHidden }
        }
        val isSearching = searchState.isSearching
        if (!isSearching) {
            files = files.sortedWith(topBar.sortOptions.createComparator())
        }
        if (isSearching != lastIsSearching) {
            lastIsSearching = isSearching
            _events.trySend(FileListEvent.ScrollTo(ScrollPosition(0, 0)))
        }
        _uiState.update { it.copy(content = it.content.copy(files = files)) }
    }

    private fun onPickOptionsChanged(pickOptions: PickOptions?) {
        _uiState.update {
            it.copy(
                content = it.content.copy(pickOptions = pickOptions),
                topBar = it.topBar.copy(
                    pickOptions = pickOptions,
                    isSelectAllVisible = pickOptions == null || pickOptions.allowMultiple,
                ),
            )
        }
        updateSelectionBar()
        updateBottomBar()
    }

    private fun onSelectedFilesChanged(files: FileItemSet) {
        updateSelectionBar()
        // A snapshot: FileItemSet is changed in place.
        val selectedPaths = files.mapTo(mutableSetOf()) { it.path }
        _uiState.update { it.copy(content = it.content.copy(selectedPaths = selectedPaths)) }
    }

    // Was FileListFragment.updateOverlayToolbar() with menu/file_list_select.xml and
    // file_list_pick.xml. When hidden, the bar fades out with its old title and actions.
    private fun updateSelectionBar() {
        val files = selectedFiles
        if (files.isEmpty()) {
            _uiState.update { it.copy(selectionBar = it.selectionBar.copy(isVisible = false)) }
            return
        }
        val pickOptions = pickOptions
        val actions = if (pickOptions != null) {
            val isOpen = when (pickOptions.mode) {
                PickOptions.Mode.OPEN_FILE, PickOptions.Mode.OPEN_DIRECTORY -> true
                PickOptions.Mode.CREATE_FILE -> false
            }
            buildList {
                add(if (isOpen) FileListSelectionAction.OPEN else FileListSelectionAction.CREATE)
                if (pickOptions.allowMultiple) {
                    add(FileListSelectionAction.SELECT_ALL)
                }
            }
        } else {
            val isAnyFileReadOnly = files.any { it.path.fileSystem.isReadOnly }
            val areAllFilesArchivePaths = files.all { it.path.isArchivePath }
            val areAllFilesArchiveFiles = files.all { it.isArchiveFile }
            val isCurrentPathReadOnly = currentPath.fileSystem.isReadOnly
            buildList {
                if (!isAnyFileReadOnly) {
                    add(FileListSelectionAction.CUT)
                }
                add(
                    if (areAllFilesArchivePaths) {
                        FileListSelectionAction.COPY_EXTRACT
                    } else {
                        FileListSelectionAction.COPY
                    }
                )
                if (!isAnyFileReadOnly) {
                    add(FileListSelectionAction.DELETE)
                }
                if (areAllFilesArchiveFiles) {
                    add(FileListSelectionAction.EXTRACT)
                }
                if (!isCurrentPathReadOnly) {
                    add(FileListSelectionAction.ARCHIVE)
                }
                add(FileListSelectionAction.SHARE)
                add(FileListSelectionAction.SELECT_ALL)
            }
        }
        val selectionBar = FileListUiState.SelectionBar(true, files.size, actions)
        _uiState.update { it.copy(selectionBar = selectionBar) }
    }

    // Was FileListFragment.updateBottomToolbar() with menu/file_list_paste.xml and
    // file_list_pick_bottom.xml. When hidden, the bar slides out with its old content.
    private fun updateBottomBar() {
        val pickOptions = pickOptions
        val bottomBar = if (pickOptions != null) {
            when (pickOptions.mode) {
                PickOptions.Mode.CREATE_FILE ->
                    FileListUiState.BottomBar(
                        isVisible = true,
                        title = FileListUiState.BottomBarTitle.FileName,
                        action = FileListBottomBarAction.CREATE,
                        isActionEnabled = true,
                    )
                PickOptions.Mode.OPEN_DIRECTORY ->
                    FileListUiState.BottomBar(
                        isVisible = true,
                        title = FileListUiState.BottomBarTitle.OpenDirectory(currentPath),
                        action = FileListBottomBarAction.OPEN,
                        isActionEnabled = true,
                    )
                else -> null
            }
        } else {
            val pasteState = pasteState
            val files = pasteState.files
            if (files.isNotEmpty()) {
                val areAllFilesArchivePaths = files.all { it.path.isArchivePath }
                val titleRes = if (pasteState.copy) {
                    if (areAllFilesArchivePaths) {
                        R.string.file_list_paste_extract_title_format
                    } else {
                        R.string.file_list_paste_copy_title_format
                    }
                } else {
                    R.string.file_list_paste_move_title_format
                }
                FileListUiState.BottomBar(
                    isVisible = true,
                    title = FileListUiState.BottomBarTitle.Paste(titleRes, files.size),
                    action = if (areAllFilesArchivePaths) {
                        FileListBottomBarAction.EXTRACT_HERE
                    } else {
                        FileListBottomBarAction.PASTE
                    },
                    isActionEnabled = !currentPath.fileSystem.isReadOnly,
                )
            } else {
                null
            }
        }
        _uiState.update { it.copy(bottomBar = bottomBar ?: it.bottomBar.copy(isVisible = false)) }
    }

    override fun onCleared() {
        _fileListLiveData.close()
    }

    companion object {
        private val _pasteStateLiveData = MutableLiveData(PasteState())
    }

    private class FileListSwitchMapLiveData(
        private val pathLiveData: LiveData<Path>,
        private val searchStateLiveData: LiveData<SearchState>
    ) : MediatorLiveData<Stateful<List<FileItem>>>(), Closeable {
        private var liveData: CloseableLiveData<Stateful<List<FileItem>>>? = null

        init {
            addSource(pathLiveData) { updateSource() }
            addSource(searchStateLiveData) { updateSource() }
        }

        private fun updateSource() {
            liveData?.let {
                removeSource(it)
                it.close()
            }
            val path = pathLiveData.valueCompat
            val searchState = searchStateLiveData.valueCompat
            val liveData = if (searchState.isSearching) {
                SearchFileListLiveData(path, searchState.query)
            } else {
                FileListLiveData(path)
            }
            this.liveData = liveData
            addSource(liveData) { value = it }
        }

        fun reload() {
            when (val liveData = liveData) {
                is FileListLiveData -> liveData.loadValue()
                is SearchFileListLiveData -> liveData.loadValue()
            }
        }

        override fun close() {
            liveData?.let {
                removeSource(it)
                it.close()
                this.liveData = null
            }
        }
    }
}

// mftlrs: the main screen state; each part goes to its own composable.
internal data class FileListUiState(
    val content: Content = Content(),
    val topBar: TopBar = TopBar(),
    val selectionBar: SelectionBar = SelectionBar(),
    val bottomBar: BottomBar = BottomBar(),
    val isFabExpanded: Boolean = false,
    val isNavigationOpen: Boolean = false, // the modal navigation drawer
) {
    data class Content(
        val files: List<FileItem> = emptyList(), // filtered and sorted
        val isRefreshing: Boolean = false, // loading when there are files already
        val isLoading: Boolean = false, // loading without files
        val errorText: String? = null,
        val isEmpty: Boolean = false,
        val viewType: FileViewType = FileViewType.LIST,
        val selectedPaths: Set<Path> = emptySet(),
        val pickOptions: PickOptions? = null,
        val nameEllipsize: TextUtils.TruncateAt = TextUtils.TruncateAt.END,
    )

    data class TopBar(
        val pickOptions: PickOptions? = null, // the title depends on it
        val subtitle: Subtitle? = null,
        val isSearchExpanded: Boolean = false,
        val searchQuery: String = "", // the initial text of the search field
        val viewType: FileViewType = FileViewType.LIST,
        val sortOptions: FileSortOptions = FileSortOptions(By.NAME, Order.ASCENDING, true),
        val isViewSortPathSpecific: Boolean = false,
        val isSelectAllVisible: Boolean = true,
        val showHiddenFiles: Boolean = false,
    )

    sealed interface Subtitle {
        data object Loading : Subtitle

        data object Error : Subtitle

        data class Counts(val directoryCount: Int, val fileCount: Int) : Subtitle
    }

    data class SelectionBar(
        val isVisible: Boolean = false,
        val count: Int = 0,
        val actions: List<FileListSelectionAction> = emptyList(),
    )

    data class BottomBar(
        val isVisible: Boolean = false,
        val title: BottomBarTitle = BottomBarTitle.FileName,
        val action: FileListBottomBarAction = FileListBottomBarAction.PASTE,
        val isActionEnabled: Boolean = true,
    )

    sealed interface BottomBarTitle {
        data object FileName : BottomBarTitle // the file name field instead of the title

        data class OpenDirectory(val path: Path) : BottomBarTitle

        data class Paste(@StringRes val formatRes: Int, val count: Int) : BottomBarTitle
    }
}

// mftlrs: one-time events of the main screen.
internal sealed interface FileListEvent {
    data class ScrollTo(val position: ScrollPosition) : FileListEvent

    data class ShowError(val text: String) : FileListEvent // a toast, when there are files already
}
