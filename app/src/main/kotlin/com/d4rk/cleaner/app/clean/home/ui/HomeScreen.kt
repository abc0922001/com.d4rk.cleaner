package com.d4rk.cleaner.app.clean.home.ui

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.d4rk.android.libs.apptoolkit.core.domain.model.ui.UiStateScreen
import com.d4rk.android.libs.apptoolkit.core.ui.components.snackbar.DefaultSnackbarHandler
import com.d4rk.android.libs.apptoolkit.core.utils.constants.ui.SizeConstants
import com.d4rk.cleaner.app.apps.manager.domain.actions.AppManagerEvent
import com.d4rk.cleaner.app.clean.analyze.ui.AnalyzeScreen
import com.d4rk.cleaner.app.clean.home.domain.actions.HomeEvent
import com.d4rk.cleaner.app.clean.home.domain.data.model.ui.UiHomeModel
import com.d4rk.cleaner.app.clean.home.ui.components.ExtraStorageInfo
import com.d4rk.cleaner.app.clean.home.ui.components.StorageProgressButton
import com.d4rk.cleaner.core.utils.helpers.PermissionsHelper
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(paddingValues: PaddingValues, snackbarHostState: SnackbarHostState) {
    val context: Context = LocalContext.current
    val view: View = LocalView.current
    val viewModel: HomeViewModel = koinViewModel()
    val uiState: UiStateScreen<UiHomeModel> by viewModel.uiState.collectAsState()

    LaunchedEffect(key1 = true) {
        if (!PermissionsHelper.hasStoragePermissions(context)) {
            PermissionsHelper.requestStoragePermissions(context as Activity)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Box(
            modifier = Modifier
                .weight(4f)
                .fillMaxWidth()
        ) {
            if (uiState.data?.analyzeState?.isAnalyzeScreenVisible == false) {
                StorageProgressButton(
                    progress = uiState.data?.storageInfo?.storageUsageProgress ?: 0f,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 98.dp),
                    onClick = {
                        viewModel.onEvent(HomeEvent.ToggleAnalyzeScreen(true))
                    }
                )

                ExtraStorageInfo(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = SizeConstants.LargeSize),
                    cleanedSpace = uiState.data?.storageInfo?.cleanedSpace ?: "",
                    freeSpace = "${uiState.data?.storageInfo?.freeSpacePercentage ?: 0} %",
                    isCleanedSpaceLoading = uiState.data?.storageInfo?.isCleanedSpaceLoading == true,
                    isFreeSpaceLoading = uiState.data?.storageInfo?.isFreeSpaceLoading == true
                )
            }

            Crossfade(
                targetState = uiState.data?.analyzeState?.isAnalyzeScreenVisible,
                animationSpec = tween(durationMillis = 300),
                label = ""
            ) { showCleaningComposable ->
                if (showCleaningComposable == true) {
                    uiState.data?.let { data ->
                        key(data.analyzeState.fileTypesData) {
                            AnalyzeScreen(
                                view = view,
                                viewModel = viewModel,
                                data = data
                            )
                        }
                    }
                }
            }
        }
    }

    DefaultSnackbarHandler(screenState = uiState , snackbarHostState = snackbarHostState , getDismissEvent = { HomeEvent.DismissSnackbar } , onEvent = { viewModel.onEvent(event = it) })
}