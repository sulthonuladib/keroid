package com.sulthonuladib.keroid.ui.main

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import com.sulthonuladib.keroid.MainNavigation
import com.sulthonuladib.keroid.data.*
import com.sulthonuladib.keroid.term.TerminalAppearance
import com.sulthonuladib.keroid.ui.theme.KeroidTheme

class ConnectionScreenTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    @After fun after() { store.clear() }

    private val actionCalls = mutableListOf<String>()
    private var mutationFails = false
    private var testWindow: android.view.Window? = null

    private fun viewModel(library: ConnectionLibrary = ConnectionLibrary(), initialState: KeroState = KeroState(),
                          beforeSave: () -> Unit = {}): MainViewModel {
        val vault = object : LibraryVault {
            var current = library
            override fun load() = current
            override fun save(library: ConnectionLibrary) { beforeSave(); current = library }
        }
        val factory = object : RemoteMachineFactory {
            override fun create(): RemoteMachine = object : RemoteMachine {
                override val state = MutableStateFlow(initialState)
                override val connectionError = MutableStateFlow<String?>(null)
                override suspend fun connect(profile: ConnectionProfile, privateKey: String, passphrase: String) {}
                override suspend fun listDirectory(dir: String, hidden: Boolean) = RemoteDirectory("/home/test", entries = listOf(RemoteDirectoryEntry("work", dir = true)))
                override suspend fun openProject(dir: String) = KeroProject("project", "work", dir)
                override suspend fun createSession(request: CreateSessionRequest) =
                    KeroSession("new-session", request.project, dir = request.dir, panes = listOf(KeroPane("new-pane")))
                override suspend fun renameSession(sessionId: String, name: String) {
                    actionCalls += "rename:$sessionId:$name"
                    check(!mutationFails)
                    state.value = state.value.copy(sessions = state.value.sessions.map {
                        if (it.id == sessionId) it.copy(name = name) else it
                    })
                }
                override suspend fun closeSession(sessionId: String) {
                    actionCalls += "close:$sessionId"
                    check(!mutationFails)
                    state.value = state.value.copy(sessions = state.value.sessions.filterNot { it.id == sessionId })
                }
                override suspend fun closePane(paneId: String) {
                    actionCalls += "pane.close:$paneId"
                    check(!mutationFails)
                    state.value = state.value.copy(sessions = state.value.sessions.map {
                        val panes = it.panes.filterNot { pane -> pane.id == paneId }
                        it.copy(panes = panes, focus = panes.firstOrNull()?.id.orEmpty())
                    })
                }
                override suspend fun read(paneId: String) = ""
                override suspend fun send(paneId: String, text: String) {}
                override suspend fun attach(paneId: String, cols: Int, rows: Int): TerminalStream? = null
                override fun disconnect() {}
            }
        }
        val inspector = object : IdentityInspector {
            override fun inspect(privateKey: String) = IdentityInfo("SHA256:test-public", "ssh-ed25519", false)
        }
        return MainViewModel(factory, vault, inspector, preferences(), SavedStateHandle(), Dispatchers.IO).also { store.put("main", it) }
    }
    private fun preferences(): Preferences = object : Preferences {
        private val mode = MutableStateFlow(ThemeMode.System)
        override val theme = mode
        override fun setTheme(mode: ThemeMode) { this.mode.value = mode }
        private val appearance = MutableStateFlow(TerminalAppearance())
        override val terminalAppearance = appearance
        override fun setTerminalAppearance(appearance: TerminalAppearance) {
            this.appearance.value = appearance
        }
    }
    /**
     * Window size and insets. A queued IME show or an inset handover between the
     * outgoing and incoming entries can legitimately reflow the outgoing screen
     * once; only destination-driven changes are a regression.
     */
    private fun windowSignature(): String {
        val window = testWindow ?: return ""
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView)
            ?: return "${window.decorView.width}x${window.decorView.height}"
        val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or
            androidx.core.view.WindowInsetsCompat.Type.ime())
        return "${window.decorView.width}x${window.decorView.height}:" +
            "${bars.left},${bars.top},${bars.right},${bars.bottom}"
    }

    private fun start(vm: MainViewModel) {
        compose.setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            SideEffect {
                var owner = context
                while (owner is android.content.ContextWrapper && owner !is android.app.Activity) owner = owner.baseContext
                testWindow = (owner as android.app.Activity).window
            }
            KeroidTheme(dynamicColor = false) { MainNavigation(vm, dark = false) }
        }
        compose.waitUntil { !vm.ui.value.restoring }
    }

    @Test fun savedMachineExitsWithoutEditorReappearingOrResetting() = machineSaveTransition(false)

    @Test fun savingDuringEditorEntryNeverFlashesEditorAgain() = machineSaveTransition(true)

    @Test fun saveDoesNotSuperimposeEditorAndDestinationTitles() = machineSaveTransition(false, noBlending = true)

    @Test fun openingEditorDoesNotSuperimposeDestinationTitles() {
        start(viewModel(actionLibrary()))
        compose.onNodeWithTag("tab-machines").performClick()
        compose.waitForIdle()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("Connections").fetchSemanticsNode().boundsInRoot
        val region = androidx.compose.ui.geometry.Rect(0f, title.top - root.top, root.width, title.bottom - root.top)
        val before = headerLuma(compose.onRoot().captureToImage().toPixelMap(), region)
        val frames = mutableListOf<ByteArray>()
        val overlaps = mutableListOf<Boolean>()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithContentDescription("Edit Dev").performClick()
            repeat(60) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                val previous = compose.onAllNodesWithText("Connections").fetchSemanticsNodes()
                val next = compose.onAllNodesWithText("Edit machine").fetchSemanticsNodes()
                overlaps += previous.any { old -> next.any { old.boundsInRoot.overlaps(it.boundsInRoot) } }
                frames += headerLuma(compose.onRoot().captureToImage().toPixelMap(), region)
            }
        } finally { compose.mainClock.autoAdvance = true }
        compose.onNodeWithText("Edit machine").assertExists()
        assertNoHeaderBlend(before, headerLuma(compose.onRoot().captureToImage().toPixelMap(), region), frames, overlaps)
    }

    private fun machineSaveTransition(earlySave: Boolean, noBlending: Boolean = false) {
        val gate = java.util.concurrent.CountDownLatch(1)
        val vm = viewModel(actionLibrary(), beforeSave = { check(gate.await(5, java.util.concurrent.TimeUnit.SECONDS)) })
        start(vm)
        compose.onNodeWithTag("tab-machines").performClick()
        compose.waitForIdle()
        if (earlySave) compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Edit Dev").performClick()
        if (earlySave) {
            repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        }
        val expectedName = if (earlySave) "Dev" else "Updated fixture"
        if (!earlySave) compose.onNodeWithText("Machine name").performTextReplacement(expectedName)
        compose.onNodeWithText("Save machine").performScrollTo()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val titleBounds = compose.onNodeWithText("Edit machine").fetchSemanticsNode().boundsInRoot
        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val header = androidx.compose.ui.geometry.Rect(0f, titleBounds.top - rootBounds.top,
            (titleBounds.right - rootBounds.left + 16f).coerceAtMost(rootBounds.width), titleBounds.bottom - rootBounds.top)
        val beforeHeader = if (noBlending) headerLuma(compose.onRoot().captureToImage().toPixelMap(), header) else null
        val headerFrames = mutableListOf<ByteArray>()
        val overlappingTitles = mutableListOf<Boolean>()
        // This part of the fixture editor title is outside the shorter
        // destination title. Unlike Save, it has no enabled/pressed color animation.
        val sampleLeft = (titleBounds.right - titleBounds.width / 3 - rootBounds.left).toInt()
        val scrollMatcher = hasScrollAction() and hasAnyDescendant(hasText("Save machine"))
        // Geometry the entry leaves with; a resize anywhere later in the exit is
        // the regression this guards.
        var editorSize: androidx.compose.ui.unit.IntSize? = null
        var insets = windowSignature()
        var resizedDuringExit = false
        val exitLevels = mutableListOf<Float>()
        val editorPositions = mutableListOf<Float?>()
        val frames = mutableListOf<String>()
        val editorLeft = mutableListOf<Float>()
        var vanished = false
        var reappeared = false
        var reset = false
        try {
            compose.runOnIdle { assertEquals(expectedName, vm.ui.value.draft.name) }
            if (earlySave) {
                // Exercise an action queued during entrance without pretending
                // an offscreen sliding button can receive a physical tap.
                compose.onNode(hasText("Save machine") and hasClickAction())
                    .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
            } else compose.onNodeWithText("Save machine").performClick()
            compose.waitUntil(5000) { vm.ui.value.saving }
            gate.countDown()
            compose.waitUntil(5000) { !vm.ui.value.saving }
            repeat(60) { frame ->
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                val titles = compose.onAllNodesWithText("Edit machine").fetchSemanticsNodes()
                    .filterNot { it.boundsInRoot.isEmpty }
                editorPositions += titles.firstOrNull()?.boundsInRoot?.left
                val fields = compose.onAllNodesWithText("Machine name").fetchSemanticsNodes()
                val outgoingSizes = compose.onAllNodes(scrollMatcher).fetchSemanticsNodes().map { it.size }
                val frameInsets = windowSignature()
                if (frameInsets != insets) {
                    insets = frameInsets
                    outgoingSizes.firstOrNull()?.let { editorSize = it }
                } else if (outgoingSizes.isNotEmpty()) {
                    // Take the exit's own geometry, then hold it for every frame.
                    editorSize = editorSize ?: outgoingSizes.first()
                }
                val values = fields.mapNotNull { node ->
                    if (node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.EditableText))
                        node.config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text else null
                }
                if (titles.isEmpty()) vanished = true else {
                    if (vanished) reappeared = true
                    editorLeft += titles.first().boundsInRoot.left
                }
                if (values.any { it != expectedName }) reset = true
                val pixels = compose.onRoot().captureToImage().toPixelMap()
                if (noBlending) {
                    val destinationTitles = compose.onAllNodesWithText("Connections").fetchSemanticsNodes()
                    overlappingTitles += titles.any { old -> destinationTitles.any { old.boundsInRoot.overlaps(it.boundsInRoot) } }
                    headerFrames += headerLuma(pixels, header)
                }
                var level = 0f
                titles.firstOrNull()?.boundsInRoot?.let { bounds ->
                    val left = maxOf(sampleLeft, (bounds.left - rootBounds.left).toInt()).coerceIn(0, pixels.width)
                    val right = (bounds.right - rootBounds.left).toInt().coerceIn(left, pixels.width)
                    val top = (bounds.top - rootBounds.top).toInt().coerceIn(0, pixels.height)
                    val bottom = (bounds.bottom - rootBounds.top).toInt().coerceIn(top, pixels.height)
                    for (y in top until bottom) for (x in left until right) {
                        val pixel = pixels[x, y]
                        level = maxOf(level, pixel.red + pixel.green + pixel.blue)
                    }
                }
                exitLevels += level
                // An interrupted entrance can leave a fully transparent entry
                // composed briefly. Its clipped bounds are not a visible jump.
                if (level > 0.4f && outgoingSizes.any { editorSize != null && it != editorSize })
                    resizedDuringExit = true
                frames += "$frame: editor=${titles.map { it.boundsInRoot }} field=${values.map { "<$it>" }} sizes=${compose.onAllNodes(scrollMatcher).fetchSemanticsNodes().map { it.size }} bounds=${compose.onAllNodes(scrollMatcher).fetchSemanticsNodes().map { it.boundsInRoot }} insets=$insets expectedSize=$editorSize exit=${exitLevels.last()} previous=${compose.onAllNodesWithText("Connections").fetchSemanticsNodes().map { it.boundsInRoot }}"
            }
        } finally {
            gate.countDown()
            compose.mainClock.autoAdvance = true
        }
        val trace = frames.joinToString("\n")
        val opacityReversed = (1 until exitLevels.size).any { i ->
            val previous = editorPositions[i - 1]
            val current = editorPositions[i]
            // Translation changes glyph pixel alignment; compare opacity only
            // at a stationary position. Movement has its own direction check.
            previous != null && current != null && kotlin.math.abs(current - previous) < 1f &&
                exitLevels[i] > exitLevels[i - 1] + 0.04f
        }
        assertFalse("Editor flashed back during exit:\n$trace", opacityReversed)
        assertFalse("Editor reappeared after leaving:\n$trace", reappeared)
        assertTrue("Editor exit reversed direction:\n$trace", editorLeft.zipWithNext().all { (a, b) -> b >= a - 1f })
        assertFalse("Outgoing editor reset while still visible:\n$trace", reset)
        assertFalse("Visible outgoing editor resized during exit:\n$trace", resizedDuringExit)
        compose.onNodeWithText("Connections").assertExists()
        compose.onNodeWithText(expectedName).assertExists()
        if (noBlending) assertNoHeaderBlend(beforeHeader!!,
            headerLuma(compose.onRoot().captureToImage().toPixelMap(), header), headerFrames, overlappingTitles)
    }

    private fun headerLuma(pixels: androidx.compose.ui.graphics.PixelMap,
                           region: androidx.compose.ui.geometry.Rect): ByteArray {
        val left = region.left.toInt()
        val top = region.top.toInt()
        val width = region.width.toInt()
        return ByteArray(width * region.height.toInt()) { i ->
            val color = pixels[left + i % width, top + i / width]
            ((color.red + color.green + color.blue) * 85f).toInt().coerceIn(0, 255).toByte()
        }
    }

    /** Compare unique title glyphs against their opaque source/destination frames.
     * A monotonic fade can still ghost both screens together (the reported bug).
     * Side-by-side sliding titles are allowed; blended, overlapping titles are not. */
    private fun assertNoHeaderBlend(before: ByteArray, after: ByteArray,
                                    frames: List<ByteArray>, overlaps: List<Boolean>) {
        fun ByteArray.luma(i: Int) = this[i].toInt() and 255
        val oldGlyphs = before.indices.filter { before.luma(it) > 190 && after.luma(it) < 70 }
        val newGlyphs = before.indices.filter { after.luma(it) > 190 && before.luma(it) < 70 }
        assertTrue("Fixture must have distinct title glyphs", oldGlyphs.size > 20 && newGlyphs.size > 20)
        val oldContrast = oldGlyphs.sumOf { (before.luma(it) - after.luma(it)).toDouble() }
        val newContrast = newGlyphs.sumOf { (after.luma(it) - before.luma(it)).toDouble() }
        val blended = frames.indices.filter { index ->
            if (!overlaps[index]) return@filter false
            val frame = frames[index]
            val oldStrength = oldGlyphs.sumOf { (frame.luma(it) - after.luma(it)).toDouble() } / oldContrast
            val newStrength = newGlyphs.sumOf { (frame.luma(it) - before.luma(it)).toDouble() } / newContrast
            oldStrength > 0.18 && newStrength > 0.18
        }
        assertTrue("Editor and saved-list titles are superimposed at animation frames $blended", blended.isEmpty())
    }

    @Test fun newIdentityKeepsItsEditorShapeUntilTheSaveTransitionEnds() = identitySaveTransition(true)

    @Test fun savedIdentityKeepsProtectionThroughExit() = identitySaveTransition(false)

    private fun identitySaveTransition(newIdentity: Boolean) {
        val gate = java.util.concurrent.CountDownLatch(1)
        val vm = viewModel(if (newIdentity) ConnectionLibrary() else actionLibrary(),
            beforeSave = { check(gate.await(5, java.util.concurrent.TimeUnit.SECONDS)) })
        start(vm)
        compose.onNodeWithTag("tab-machines").performClick()
        compose.onNodeWithText("Identities").performClick()
        compose.waitForIdle()
        if (newIdentity) compose.onNodeWithText("Add identity", useUnmergedTree = true).performClick()
        else compose.onNodeWithText("Work").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Identity name").performTextReplacement("Synthetic identity")
        if (newIdentity) compose.onNodeWithText("Private key").performTextInput("test-only-key")
        compose.onNodeWithText("Save identity").performScrollTo()
        val scrollMatcher = hasScrollAction() and hasAnyDescendant(hasText("Save identity"))
        var before: androidx.compose.ui.unit.IntSize? = null
        var insets = windowSignature()
        var changedMode = false
        var resized = false
        var unprotectedDuringExit = false
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithText("Save identity").performClick()
            compose.waitUntil(5000) { vm.ui.value.saving }
            gate.countDown()
            compose.waitUntil(5000) { !vm.ui.value.saving }
            repeat(60) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                if (newIdentity && compose.onAllNodesWithText("Edit identity").fetchSemanticsNodes().isNotEmpty()) changedMode = true
                val editors = compose.onAllNodes(scrollMatcher).fetchSemanticsNodes()
                val frameInsets = windowSignature()
                if (frameInsets != insets) {
                    insets = frameInsets
                    editors.firstOrNull()?.let { before = it.size }
                } else if (editors.isNotEmpty()) {
                    before = before ?: editors.first().size
                }
                if (editors.any { node -> before != null && node.size != before }) resized = true
                if (editors.isNotEmpty()) compose.runOnIdle {
                    if (testWindow!!.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE == 0)
                        unprotectedDuringExit = true
                }
            }
        } finally {
            gate.countDown()
            compose.mainClock.autoAdvance = true
        }
        assertFalse("Outgoing Add identity flashed as Edit identity after commit", changedMode)
        assertFalse("Destination tabs resized the outgoing identity editor", resized)
        assertFalse("FLAG_SECURE was toggled off while the identity editor was still drawing", unprotectedDuringExit)
        compose.onNodeWithText("Connections").assertExists()
        compose.onNodeWithText("Synthetic identity").assertExists()
        assertTrue("Private key must still be cleared immediately", vm.privateKey.value.isEmpty())
    }

    @Test fun openProjectBrowsesRemoteFoldersAndNavigatesToCreatedSession() {
        val library = ConnectionLibrary(
            listOf(StoredIdentity(Identity("key", "Work", requiresPassphrase = false), "test-only-key")),
            listOf(Machine("a", "Dev", "devbox", "alice", "key")))
        val vm = viewModel(library)
        start(vm)
        compose.onNodeWithText("Connect", substring = false).performClick()
        compose.waitUntil { vm.ui.value.connection("a").connected }
        compose.onNodeWithContentDescription("New session or project").performClick()
        compose.onNodeWithText("Open project…").performClick()
        compose.onNodeWithText("Remote folder").assertExists()
        compose.waitUntil { vm.ui.value.browser.directory != null }
        compose.onNodeWithText("work", substring = false).assertExists()
        compose.onNodeWithText("Open folder and start session").performClick()
        compose.waitUntil { vm.ui.value.connection("a").state.sessions.any { it.id == "new-session" } }
        compose.onNodeWithContentDescription("Terminal actions").assertExists()
    }

    private fun actionLibrary() = ConnectionLibrary(
        listOf(StoredIdentity(Identity("key", "Work", requiresPassphrase = false), "test-only-key")),
        listOf(Machine("a", "Dev", "devbox", "alice", "key")))

    private fun actionState() = KeroState(sessions = listOf(KeroSession("session", "project", name = "Original",
        focus = "pane", panes = listOf(KeroPane("pane"), KeroPane("other", title = "Other")))))

    private fun connect(vm: MainViewModel) {
        compose.onNodeWithText("Connect", substring = false).performClick()
        compose.waitUntil { vm.ui.value.connection("a").connected }
    }

    @Test fun homeRenameDraftSurvivesRestorationAndCancelDoesNotMutate() {
        val vm = viewModel(actionLibrary(), actionState())
        val restoration = StateRestorationTester(compose)
        restoration.setContent { KeroidTheme(dynamicColor = false) { MainNavigation(vm, dark = false) } }
        compose.waitUntil { !vm.ui.value.restoring }
        connect(vm)
        compose.onNodeWithContentDescription("Actions for Original").performClick()
        compose.onNodeWithText("Rename session").performClick()
        compose.onNodeWithText("Session name").performTextReplacement("Draft name")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Session name").assertTextContains("Draft name")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(actionCalls.isEmpty()) }
        compose.onNodeWithContentDescription("Actions for Original").performClick()
        compose.onNodeWithText("Rename session").performClick()
        compose.onNodeWithText("Session name").performTextReplacement("Renamed")
        compose.onNodeWithText("Save name").performClick()
        compose.waitUntil { vm.ui.value.connection("a").state.sessions.single().name == "Renamed" }
        compose.onNodeWithText("Renamed").assertExists()
        compose.runOnIdle { assertEquals(listOf("rename:session:Renamed"), actionCalls) }
    }

    @Test fun homeCloseRequiresConfirmationAndKeepsWorktree() {
        val vm = viewModel(actionLibrary(), actionState())
        start(vm); connect(vm)
        compose.onNodeWithContentDescription("Actions for Original").performClick()
        compose.onNodeWithText("Close session").performClick()
        compose.onNodeWithText("Any linked worktree will be kept.", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(actionCalls.isEmpty()) }
        compose.onNodeWithContentDescription("Actions for Original").performClick()
        compose.onNodeWithText("Close session").performClick()
        compose.onNode(hasText("Close session") and hasClickAction()).performClick()
        compose.waitUntil { vm.ui.value.connection("a").state.sessions.isEmpty() }
        compose.onNodeWithText("No sessions on this machine.").assertExists()
        compose.runOnIdle { assertEquals(listOf("close:session"), actionCalls) }
    }

    @Test fun terminalCanRenameCloseSelectedPaneAndCloseSessionBackToHome() {
        val vm = viewModel(actionLibrary(), actionState())
        start(vm); connect(vm)
        compose.onNodeWithText("Original").performClick()
        compose.onNodeWithContentDescription("Terminal actions").performClick()
        compose.onNodeWithText("Rename session").performClick()
        compose.onNodeWithText("Session name").performTextReplacement("Terminal name")
        compose.onNodeWithText("Save name").performClick()
        compose.waitUntil { vm.ui.value.connection("a").state.sessions.single().name == "Terminal name" }
        compose.onNodeWithText("2 · Other").performClick()
        compose.onNodeWithContentDescription("Terminal actions").performClick()
        compose.onNodeWithText("Close pane").performClick()
        compose.onNodeWithText("pane “Other”", substring = true).assertExists()
        compose.runOnIdle { assertEquals(1, actionCalls.size) }
        compose.onNode(hasText("Close pane") and hasClickAction()).performClick()
        compose.waitUntil { vm.ui.value.connection("a").state.sessions.single().panes.size == 1 }
        compose.onNodeWithContentDescription("Terminal actions").assertExists()
        compose.onNodeWithText("2 · Other").assertDoesNotExist()
        compose.onNodeWithContentDescription("Terminal actions").performClick()
        compose.onNodeWithText("Close pane").assertDoesNotExist()
        compose.onNodeWithText("Close session").performClick()
        compose.onNode(hasText("Close session") and hasClickAction()).performClick()
        compose.waitUntil { vm.ui.value.connection("a").state.sessions.isEmpty() }
        compose.onNodeWithContentDescription("Search sessions").assertExists()
        compose.runOnIdle { assertEquals(listOf("rename:session:Terminal name", "pane.close:other", "close:session"), actionCalls) }
    }

    @Test fun closingNewSessionReturnsToPreviousTerminalNotPastIt() {
        val vm = viewModel(actionLibrary(), actionState())
        start(vm); connect(vm)
        compose.onNodeWithText("Original").performClick()
        compose.onNodeWithContentDescription("Terminal actions").performClick()
        compose.onNodeWithText("New session").performClick()
        compose.waitUntil { vm.ui.value.connection("a").state.sessions.any { it.id == "new-session" } }
        compose.onNodeWithContentDescription("Terminal actions").performClick()
        compose.onNodeWithText("Close session").performClick()
        compose.onNode(hasText("Close session") and hasClickAction()).performClick()
        compose.waitUntil { vm.ui.value.connection("a").state.sessions.none { it.id == "new-session" } }
        compose.onNodeWithText("Original").assertExists()
        compose.onNodeWithContentDescription("Terminal actions").assertExists()
        compose.onNodeWithContentDescription("Search sessions").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("close:new-session"), actionCalls) }
    }

    @Test fun failedCloseShowsUncertainResultWithoutAutomaticRetry() {
        mutationFails = true
        val vm = viewModel(actionLibrary(), actionState())
        start(vm); connect(vm)
        compose.onNodeWithContentDescription("Actions for Original").performClick()
        compose.onNodeWithText("Close session").performClick()
        compose.onNode(hasText("Close session") and hasClickAction()).performClick()
        compose.waitUntil { vm.ui.value.actions["a"]?.error != null }
        compose.onNode(hasText("The action may not have completed.", substring = true) and
            hasAnyAncestor(isDialog())).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(listOf("close:session"), actionCalls) }
        compose.onNodeWithText("Original").assertExists()
    }

    @Test fun machineEditorOffersTailscaleWithoutAnIdentityOrSshFields() {
        val vm = viewModel()
        start(vm)
        compose.onNodeWithText("Add a machine").performClick()
        compose.onNodeWithText("Machine name").performTextInput("Tail")
        // SSH is the default and asks for an identity.
        compose.onNodeWithText("SSH address").assertExists()
        compose.onNodeWithText("Choose identity").assertExists()
        compose.onNodeWithText("Tailscale").performClick()
        compose.onNodeWithText("SSH address").assertDoesNotExist()
        compose.onNodeWithText("Choose identity").assertDoesNotExist()
        compose.onNodeWithText("Tailscale address").performTextInput("https://devbox.tail1234.ts.net")
        compose.onNodeWithText("Save machine").performScrollTo().performClick()
        compose.waitUntil { vm.ui.value.machines.any { it.name == "Tail" } }
        val machine = vm.ui.value.machines.first { it.name == "Tail" }
        assertTrue("saved as a Tailscale machine", machine.isTailscale)
        assertEquals("", machine.identityId)
        assertEquals("https://devbox.tail1234.ts.net:443", machine.endpoint)
        // The machines list shows the tailnet address, not an SSH destination.
        compose.onNodeWithTag("tab-machines").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Tail").assertExists()
        compose.onNodeWithText("https://devbox.tail1234.ts.net:443").assertExists()
        compose.onNodeWithText("Tailscale · Disconnected", substring = true).assertExists()
    }

    @Test fun tailscaleEditorRejectsABadAddressAndSaysWhy() {
        val vm = viewModel()
        start(vm)
        compose.onNodeWithText("Add a machine").performClick()
        compose.onNodeWithText("Machine name").performTextInput("Tail")
        compose.onNodeWithText("Tailscale").performClick()
        compose.onNodeWithText("Tailscale address").performTextInput("ssh://devbox")
        compose.onNodeWithText("Save machine").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Use http:// for kerod's tailnet listener or https:// for Tailscale Serve.")
            .assertExists()
        assertTrue(vm.ui.value.machines.isEmpty())
    }

    @Test fun compactHomeIsSearchBehindAButtonWithoutMarketingOrOnboardingForm() {
        start(viewModel())
        compose.onNodeWithText("Keroid").assertExists()
        compose.onNodeWithContentDescription("Search sessions").assertExists()
        compose.onNodeWithTag("tab-sessions").assertExists()
        compose.onNodeWithTag("tab-machines").assertExists()
        compose.onNodeWithText("No machines yet").assertExists()
        compose.onNodeWithText("Add a machine").assertExists()
        compose.onNodeWithText("Your agents.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("A little less watching", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Private key").assertDoesNotExist()
        // The search field is not on screen until it is asked for.
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test fun searchOpensFromTheAppBarAndFiltersSessions() {
        val library = ConnectionLibrary(
            listOf(StoredIdentity(Identity("key", "Work", requiresPassphrase = false), "test-only-key")),
            listOf(Machine("a", "Dev", "devbox", "alice", "key")))
        val vm = viewModel(library)
        start(vm)
        compose.onNodeWithContentDescription("Search sessions").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("query")
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test fun addIdentityReturnsToMachineDraftAndSavesReusableSelection() {
        val vm = viewModel()
        start(vm)
        compose.onNodeWithText("Add a machine").performClick()
        compose.onNodeWithText("Machine name").performTextInput("Dev")
        compose.onNodeWithText("SSH address").performTextInput("alice@devbox")
        compose.onNodeWithText("Add identity").performScrollTo().performClick()
        compose.onNodeWithText("Identity name").performTextInput("Work")
        compose.onNodeWithText("Private key").performTextInput("test-only-key")
        compose.onNodeWithText("Save identity").performScrollTo().performClick()
        compose.waitUntil { vm.ui.value.identities.size == 1 }
        compose.onNodeWithText("Machine name").assertTextContains("Dev")
        compose.onNodeWithText("SSH address").assertTextContains("alice@devbox")
        compose.onNodeWithText("Work").assertExists()
        compose.onNodeWithText("Save machine").performScrollTo().performClick()
        compose.waitUntil { vm.ui.value.machines.size == 1 }
        compose.onNodeWithText("Dev").assertExists()
        assertEquals(vm.ui.value.identities.single().id, vm.ui.value.machines.single().identityId)
        assertTrue(vm.privateKey.value.isEmpty())
    }

    @Test fun portIsVisibleWithoutAdvancedAndDraftSurvivesRestoration() {
        val vm = viewModel()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { KeroidTheme(dynamicColor = false) { MainNavigation(vm, dark = false) } }
        compose.waitUntil { !vm.ui.value.restoring }
        compose.onNodeWithText("Add a machine").performClick()
        compose.onNodeWithText("Save machine").performScrollTo().performClick()
        compose.onNodeWithText("Give this machine a name.").assertExists()
        compose.onNodeWithText("Machine name").performScrollTo().performTextInput("Draft")
        compose.onNodeWithText("Port").performScrollTo().assertExists()
        compose.onNodeWithText("Port").performTextReplacement("2222")
        compose.onNodeWithText("Advanced options").performScrollTo().performClick()
        compose.onNodeWithText("Kero executable (optional)").performScrollTo().performTextInput("/opt/kero/kero")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Port").performScrollTo().assertTextContains("2222")
        compose.onNodeWithText("Machine name").performScrollTo().assertTextContains("Draft")
        compose.onNodeWithText("Kero executable (optional)").performScrollTo().assertTextContains("/opt/kero/kero")
    }

    @Test fun machineSelectorSwitchesAndSearchSurvivesNavigationRestoration() {
        val library = ConnectionLibrary(
            listOf(StoredIdentity(Identity("key", "Work", requiresPassphrase = false), "test-only-key")),
            listOf(Machine("a", "Dev", "devbox", "alice", "key"), Machine("b", "Build", "buildbox", "alice", "key")))
        val vm = viewModel(library)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { KeroidTheme(dynamicColor = false) { MainNavigation(vm, dark = false) } }
        compose.waitUntil { !vm.ui.value.restoring }
        compose.onNodeWithContentDescription("Search sessions").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("query")
        compose.onNodeWithText("Build").performClick()
        compose.runOnIdle { assertEquals("b", vm.ui.value.selectedMachineId) }
        compose.onNodeWithTag("tab-machines").performClick()
        compose.onNodeWithText("Identities").performClick()
        compose.onNodeWithText("Work").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Work").assertExists()
        compose.onNodeWithTag("tab-sessions").performClick()
        compose.onNodeWithText("query").assertExists()
        compose.onNodeWithText("Build").assertExists()
    }

    @Test fun identityManagerPreventsDeletingAnInUseKey() {
        val library = ConnectionLibrary(listOf(StoredIdentity(Identity("key", "Work"), "test-only-key")),
            listOf(Machine("a", "Dev", "devbox", "alice", "key")))
        val vm = viewModel(library)
        start(vm)
        compose.onNodeWithTag("tab-machines").performClick()
        compose.onNodeWithText("Identities").performClick()
        compose.onNodeWithText("Work").performClick()
        compose.onNodeWithText("Delete identity").performScrollTo().performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.waitUntil { vm.ui.value.editorError != null }
        compose.onNodeWithText("This identity is used by a machine.", substring = true).assertExists()
        assertEquals(1, vm.ui.value.identities.size)
    }

    @Test fun changedHostPromptHasNoTrustOrReplaceAction() {
        val machine = Machine("a", "Dev", "devbox", "alice", "key")
        compose.setContent { KeroidTheme(dynamicColor = false) {
            ConnectionPrompts(MainUiState(restoring = false, machines = listOf(machine), connections = mapOf("a" to MachineUiState(
                ConnectionStatus.VerifyHost, hostKey = HostKeyProblem("host_key_changed", "SHA256:different", "ssh-ed25519")))),
                "", {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Host key changed").assertExists()
        compose.onNodeWithText("Trust and connect").assertDoesNotExist()
        compose.onNodeWithText("Close").assertExists()
    }
}
