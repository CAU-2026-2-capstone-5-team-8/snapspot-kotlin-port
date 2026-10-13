package com.capstone.snapspot.ui.add

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capstone.snapspot.R
import com.capstone.snapspot.domain.draft.AddEvent
import com.capstone.snapspot.domain.draft.Saved
import com.capstone.snapspot.ui.theme.SnapColor
import kotlinx.coroutines.launch

/** Connects the add screen to its ViewModel, and shows the one-time messages. */
@Composable
fun AddRoute(vm: AddViewModel, onSaved: (Saved) -> Unit, onClose: () -> Unit) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val here by vm.here.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var message by remember { mutableStateOf<Pair<String, String>?>(null) }
    val readFailed = stringResource(R.string.read_failed_title)
    val noLocationTitle = stringResource(R.string.location_not_found_title)
    val noLocationBody = stringResource(R.string.location_not_found_body)
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            message = when (event) {
                is AddEvent.ReadFailed -> readFailed to event.message
                AddEvent.LocationNotFound -> noLocationTitle to noLocationBody
            }
        }
    }

    // Leaving by the back gesture drops the draft, like the close button.
    val close = {
        vm.cancel()
        onClose()
    }
    BackHandler(onBack = close)

    val d = draft
    if (d == null) {
        Box(Modifier.fillMaxSize().background(SnapColor.Paper))
    } else {
        AddContent(
            draft = d,
            here = here,
            busy = busy,
            actions = AddActions(
                onClose = close,
                onSave = { scope.launch { vm.save()?.let(onSaved) } },
                onKind = vm::setKind,
                onChange = vm::update,
                onRefindAddress = vm::regeocode,
                onPinHere = vm::pinToHere,
                onPickCandidate = vm::pickCandidate,
                onRefreshBranches = vm::refreshBranchPreview,
            ),
        )
    }

    message?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.ok)) } },
            title = { Text(title) },
            text = { Text(body) },
        )
    }
}
