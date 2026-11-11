package com.creatora.app.ui.grow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.creatora.app.R
import com.creatora.app.domain.grow.AbTest
import com.creatora.app.domain.grow.TestKind
import com.creatora.app.domain.grow.TestVariant
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.EmptyState
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.theme.CreatoraTheme

/** The A/B test log: tests the user runs by hand, with the winner noted when it is decided. */
@Composable
fun TestsTab(
    state: GrowUiState,
    onCreate: (name: String, kind: TestKind, a: String, b: String, notes: String) -> Unit,
    onWinner: (AbTest, TestVariant?) -> Unit,
    onDelete: (AbTest) -> Unit,
) {
    val colors = CreatoraTheme.colors
    var creating by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.grow_tests_intro), style = CreatoraTheme.typography.body, color = colors.textMuted)
    PrimaryButton(stringResource(R.string.grow_test_new), { creating = true })
    if (state.tests.isEmpty()) {
        EmptyState(Icons.Outlined.Science, stringResource(R.string.grow_tests_empty_title), stringResource(R.string.grow_tests_empty_body))
    }
    state.tests.forEach { test -> TestCard(test, onWinner, onDelete) }
    if (creating) NewTestDialog({ name, kind, a, b, notes -> onCreate(name, kind, a, b, notes); creating = false }, { creating = false })
}

@Composable
private fun TestCard(test: AbTest, onWinner: (AbTest, TestVariant?) -> Unit, onDelete: (AbTest) -> Unit) {
    val colors = CreatoraTheme.colors
    val shape = CreatoraTheme.shapes.radius16
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, colors.border, shape)
            .padding(CreatoraTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
    ) {
        Row {
            Text(test.name, style = CreatoraTheme.typography.cardTitle, color = colors.text, modifier = Modifier.weight(1f))
            Text(
                stringResource(if (test.kind == TestKind.TITLE) R.string.grow_kind_title else R.string.grow_kind_thumbnail),
                style = CreatoraTheme.typography.caption,
                color = colors.accentText,
            )
        }
        Variant("A", test.variantA, test.winner == TestVariant.A)
        Variant("B", test.variantB, test.winner == TestVariant.B)
        if (test.notes.isNotBlank()) Text(test.notes, style = CreatoraTheme.typography.caption, color = colors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            if (test.winner == null) {
                PrimaryButton(stringResource(R.string.grow_a_wins), { onWinner(test, TestVariant.A) })
                PrimaryButton(stringResource(R.string.grow_b_wins), { onWinner(test, TestVariant.B) })
            } else {
                SecondaryButton(stringResource(R.string.grow_reopen), { onWinner(test, null) })
            }
            SecondaryButton(stringResource(R.string.action_delete), { onDelete(test) })
        }
    }
}

@Composable
private fun Variant(label: String, text: String, winner: Boolean) {
    val colors = CreatoraTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        Text(label, style = CreatoraTheme.typography.bodyBold, color = if (winner) colors.success else colors.textMuted)
        Text(text, style = CreatoraTheme.typography.body, color = colors.text, modifier = Modifier.weight(1f))
        if (winner) Text(stringResource(R.string.grow_winner), style = CreatoraTheme.typography.caption, color = colors.success)
    }
}

@Composable
private fun NewTestDialog(onCreate: (String, TestKind, String, String, String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(TestKind.TITLE) }
    var a by rememberSaveable { mutableStateOf("") }
    var b by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.grow_test_new), style = CreatoraTheme.typography.sectionTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.grow_test_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                    ChoiceChip(stringResource(R.string.grow_kind_title), kind == TestKind.TITLE, { kind = TestKind.TITLE })
                    ChoiceChip(stringResource(R.string.grow_kind_thumbnail), kind == TestKind.THUMBNAIL, { kind = TestKind.THUMBNAIL })
                }
                OutlinedTextField(a, { a = it }, label = { Text(stringResource(R.string.grow_variant_a)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(b, { b = it }, label = { Text(stringResource(R.string.grow_variant_b)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.saved_idea_notes_label)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, kind, a, b, notes) }, enabled = name.isNotBlank() && a.isNotBlank() && b.isNotBlank()) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = CreatoraTheme.colors.surface,
    )
}
