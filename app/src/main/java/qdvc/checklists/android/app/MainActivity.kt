package qdvc.checklists.android.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import qdvc.checklists.android.app.ui.checklist.ChecklistScreen
import qdvc.checklists.android.app.ui.checklist.InfoScreen
import qdvc.checklists.android.app.ui.components.BottomBar
import qdvc.checklists.android.app.ui.home.HomeScreen
import qdvc.checklists.android.app.ui.settings.SettingsScreen
import qdvc.checklists.android.app.ui.splash.SplashScreen
import qdvc.checklists.android.app.ui.switcher.SwitcherScreen
import qdvc.checklists.android.app.ui.theme.QDVCTheme
import qdvc.checklists.android.app.ui.theme.resolveDark

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { AppRoot(vm) }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val mode by vm.themeMode.collectAsStateWithLifecycle()
    val lightId by vm.lightThemeId.collectAsStateWithLifecycle()
    val darkId by vm.darkThemeId.collectAsStateWithLifecycle()
    val dark = resolveDark(mode)
    // Reference the ids so a change recomposes and re-resolves the theme.
    val spec = remember(dark, lightId, darkId) { vm.themeFor(dark) }

    val loadState by vm.loadState.collectAsStateWithLifecycle()

    QDVCTheme(spec = spec, darkTheme = dark) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            // Everything the app shows comes from the local projection, so hold the
            // UI until the launch-time read has populated it.
            when (val state = loadState) {
                is LoadState.Starting -> SplashScreen(lines = emptyList())
                is LoadState.Loading -> SplashScreen(lines = state.lines)
                is LoadState.Ready -> AppScaffold(vm)
            }
        }
    }
}

/**
 * A [LazyListState] retained per [key] for as long as this screen lives, rather
 * than per composition, so a list returns to where the user left it.
 *
 * [retain] bounds the memory: positions for keys no longer in that set are
 * dropped, so a long session browsing many checklists doesn't accumulate them.
 */
@Composable
private fun rememberKeyedListState(key: String?, retain: Set<String>): LazyListState {
    val states = remember { mutableMapOf<String, LazyListState>() }
    val fallback = rememberLazyListState()
    LaunchedEffect(retain) { states.keys.retainAll(retain) }
    return key?.let { states.getOrPut(it) { LazyListState() } } ?: fallback
}

@Composable
private fun AppScaffold(vm: AppViewModel) {
    val context = LocalContext.current

    val tab by vm.tab.collectAsStateWithLifecycle()
    val browse by vm.browse.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val openItems by vm.openItems.collectAsStateWithLifecycle()
    val current by vm.currentItem.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val selectedItem by vm.selectedItem.collectAsStateWithLifecycle()
    val allChecklists by vm.allChecklists.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val indexStatus by vm.indexStatus.collectAsStateWithLifecycle()
    val rearranging by vm.rearranging.collectAsStateWithLifecycle()
    val rearrangePrompt by vm.rearrangePrompt.collectAsStateWithLifecycle()

    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val lightId by vm.lightThemeId.collectAsStateWithLifecycle()
    val darkId by vm.darkThemeId.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }

    // Scroll positions for the tabs that hold long lists. These are held here,
    // above the AnimatedContent that swaps tabs, because that discards the
    // outgoing tab's composition — anything remembered inside a tab would reset
    // to the top on every visit. Home needs it for the same reason twice over,
    // since its own SlideNavHost also swaps its levels in and out.
    val checklistListState = rememberKeyedListState(
        key = current?.checklistDocId,
        retain = openItems.map { it.checklistDocId }.toSet(),
    )
    val homeChecklistsListState = rememberKeyedListState(
        key = browse.workspace?.treeUri?.toString(),
        retain = workspaces.map { it.treeUri.toString() }.toSet(),
    )
    // Only ever one workspace list, so it needs no key.
    val workspacesListState = rememberLazyListState()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "Workspace"
            vm.addWorkspace(uri, name)
        }
    }

    val itemOpen = current != null

    val message by vm.message.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val m = message
        if (m != null) {
            snackbarHost.showSnackbar(m)
            vm.clearMessage()
        }
    }

    if (showSettings) {
        SettingsScreen(
            themeMode = themeMode,
            lightThemeId = lightId,
            darkThemeId = darkId,
            lightThemes = vm.lightThemes(),
            darkThemes = vm.darkThemes(),
            onSetMode = vm::setThemeMode,
            onSetLight = vm::setLightTheme,
            onSetDark = vm::setDarkTheme,
            onClose = { showSettings = false },
        )
        return
    }

    // System back follows the tab chain: Info -> Checklist -> Home. On Home it
    // mirrors the toolbar back arrow (A2/B2), walking up the browse hierarchy,
    // and is disabled at the home root so the system can close the app.
    val canGoBack = when (tab) {
        Tab.HOME -> browse.mode != BrowseMode.WORKSPACES
        Tab.VIEW, Tab.INFO, Tab.SWITCHER -> true
    }
    BackHandler(enabled = canGoBack) { vm.navigateBack() }

    Scaffold(
        bottomBar = {
            // Rearranging takes over the screen, so the tab bar slides out of the
            // way and slides back when the mode ends.
            AnimatedVisibility(
                visible = !rearranging,
                enter = slideInVertically(animationSpec = tween(220)) { it },
                exit = slideOutVertically(animationSpec = tween(220)) { it },
            ) {
                BottomBar(current = tab, itemOpen = itemOpen, onSelect = vm::selectTab)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    // Card-shuffle: the incoming screen elevates and scales up to
                    // fill while the outgoing one recedes and fades aside.
                    val enter = scaleIn(initialScale = 0.92f, animationSpec = tween(280)) +
                        fadeIn(animationSpec = tween(280))
                    val exit = slideOutHorizontally(animationSpec = tween(220)) { -it / 6 } +
                        fadeOut(animationSpec = tween(220))
                    (enter togetherWith exit)
                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                },
                label = "tab-shuffle",
            ) { shownTab ->
                when (shownTab) {
                    Tab.HOME -> HomeScreen(
                        browse = browse,
                        workspaces = workspaces,
                        workspacesListState = workspacesListState,
                        checklistsListState = homeChecklistsListState,
                        allChecklists = allChecklists,
                        searchResults = searchResults,
                        openChecklistDocId = current?.checklistDocId,
                        onAddWorkspace = { picker.launch(null) },
                        onRemoveWorkspace = vm::removeWorkspace,
                        onOpenWorkspace = vm::openWorkspace,
                        onBack = { vm.browseUp() },
                        onOpenChecklist = { c ->
                            browse.workspace?.let { vm.openChecklist(it, c) }
                        },
                        onOpenHit = { hit ->
                            browse.workspace?.let { vm.openByHit(it, hit) }
                        },
                        onSearch = vm::runSearch,
                        onSetSearching = vm::setSearching,
                        onShowIndexStatus = {
                            vm.showChecklistsSurface(ChecklistsSurface.INDEX_STATUS)
                        },
                        onRegenerateIndex = vm::regenerateIndex,
                        indexStatus = indexStatus,
                        onCreateChecklist = vm::createChecklist,
                        onOpenSettings = { showSettings = true },
                    )
                    Tab.VIEW -> ChecklistScreen(
                        loaded = loaded,
                        listState = checklistListState,
                        selectedItemDocId = selectedItem?.item?.docId,
                        rearranging = rearranging,
                        rearrangePrompt = rearrangePrompt,
                        onInspectItem = vm::inspectItem,
                        onMarkAllNotDone = vm::markAllNotDone,
                        onEditChecklist = vm::editChecklist,
                        onCreateNode = vm::createNode,
                        onStartRearrange = vm::startRearrange,
                        onAskCancelRearrange = vm::askCancelRearrange,
                        onAskSaveRearrange = vm::askSaveRearrange,
                        onDismissRearrangePrompt = vm::dismissRearrangePrompt,
                        onConfirmCancelRearrange = vm::confirmCancelRearrange,
                        onConfirmSaveRearrange = vm::confirmSaveRearrange,
                    )
                    Tab.INFO -> InfoScreen(
                        selected = selectedItem,
                        onToggleDone = vm::toggleSelectedItemDone,
                        onSkip = vm::skipSelectedItem,
                        onEditNode = vm::editNode,
                    )
                    Tab.SWITCHER -> SwitcherScreen(
                        openItems = openItems,
                        current = current,
                        onSelect = vm::selectOpenItem,
                        onClose = vm::closeOpenItem,
                    )
                }
            }
        }
    }
}
