import sys
import re

with open("app/src/main/java/com/example/fluxplay/ui/player/PlayerScreen.kt", "r") as f:
    content = f.read()

movable_block = """
    val playerSurface = remember(state.selectedEngine) {
        movableContentOf {
            if (state.selectedEngine == PlayerEngine.LIBMPV) {
                AndroidView(
                    factory = { ctx ->
                        com.example.fluxplay.ui.player.MPVPlayerViewWrapper(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            initialize(ctx.filesDir.path, ctx.cacheDir.path)
                            viewModel.attachMpv(this.mpv)
                        }
                    },
                    onRelease = { it.destroy() },
                    modifier = Modifier.fillMaxSize().testTag("mpv_view")
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            resizeMode = state.resizeMode.exoMode
                            player = viewModel.getExoPlayer()
                        }
                    },
                    update = { view ->
                        view.player = viewModel.getExoPlayer()
                        view.resizeMode = state.resizeMode.exoMode
                    },
                    modifier = Modifier.fillMaxSize().testTag("player_view")
                )
            }
        }
    }
"""

content = content.replace(
    "    var showQualitySheet by remember { mutableStateOf(false) }",
    "    var showQualitySheet by remember { mutableStateOf(false) }\n" + movable_block
)

content = re.sub(
    r'            // Android Player View.*?            // Touch & Gesture Interaction Surface',
    r'            // Android Player View\n            playerSurface()\n\n            // Touch & Gesture Interaction Surface',
    content,
    flags=re.DOTALL
)

content = re.sub(
    r'                // Player View.*?                // Tap & Gesture Detector Overlay',
    r'                // Player View\n                playerSurface()\n\n                // Tap & Gesture Detector Overlay',
    content,
    flags=re.DOTALL
)

with open("app/src/main/java/com/example/fluxplay/ui/player/PlayerScreen.kt", "w") as f:
    f.write(content)
