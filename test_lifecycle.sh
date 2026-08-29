sed -i '/val state by viewModel.uiState.collectAsStateWithLifecycle()/a \
\
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current\
    DisposableEffect(lifecycleOwner, state.settings.backgroundPlayEnabled) {\
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->\
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && !state.settings.backgroundPlayEnabled) {\
                viewModel.pause()\
            }\
        }\
        lifecycleOwner.lifecycle.addObserver(observer)\
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }\
    }\
' app/src/main/java/com/example/fluxplay/ui/player/PlayerScreen.kt
