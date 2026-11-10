package net.devemperor.tunertogo.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * A scrolling Wear list with the Material 3 scale-and-fade transformation at the edges.
 *
 * Every item below declares the minimum list padding Material 3 recommends for its type
 * (minimumVerticalContentPadding). The list uses the largest value of its first and last item,
 * so on round watches the first and last entry can always be scrolled fully inside the display
 * instead of ending under the curved edge.
 */
@Composable
fun ListScreen(content: TransformingLazyColumnScope.(TransformationSpec) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            content(spec)
        }
    }
}

@Composable
fun TransformingLazyColumnItemScope.Header(text: String, spec: TransformationSpec) {
    ListHeader(
        modifier = Modifier
            .fillMaxWidth()
            .transformedHeight(this, spec)
            .minimumVerticalContentPadding(
                top = ListHeaderDefaults.minimumTopListContentPadding,
                bottom = ListHeaderDefaults.minimumBottomListContentPadding,
            ),
        transformation = SurfaceTransformation(spec),
    ) {
        Text(text)
    }
}

@Composable
fun TransformingLazyColumnItemScope.SubHeader(text: String, spec: TransformationSpec) {
    ListSubHeader(
        modifier = Modifier
            .fillMaxWidth()
            .transformedHeight(this, spec)
            .minimumVerticalContentPadding(
                top = ListHeaderDefaults.minimumTopListContentPadding,
                bottom = ListHeaderDefaults.minimumBottomListContentPadding,
            ),
        transformation = SurfaceTransformation(spec),
    ) {
        Text(text)
    }
}

@Composable
fun TransformingLazyColumnItemScope.MenuButton(
    label: String,
    spec: TransformationSpec,
    onClick: () -> Unit,
    secondaryLabel: String? = null,
    @DrawableRes icon: Int? = null,
    highlighted: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .transformedHeight(this, spec)
            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
        transformation = SurfaceTransformation(spec),
        colors = if (highlighted) ButtonDefaults.filledVariantButtonColors() else ButtonDefaults.filledTonalButtonColors(),
        icon = icon?.let { res -> { Icon(painterResource(res), contentDescription = null) } },
        secondaryLabel = secondaryLabel?.let { text -> { Text(text, maxLines = 3, overflow = TextOverflow.Ellipsis) } },
        label = { Label(label) },
    )
}

/**
 * Item label that hyphenates long words (German "Empfindlich-keit") instead of breaking them
 * at an arbitrary letter when a large font setting leaves too little width.
 */
@Composable
private fun Label(text: String) {
    Text(
        text = text,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        style = LocalTextStyle.current.copy(hyphens = Hyphens.Auto),
    )
}

@Composable
fun TransformingLazyColumnItemScope.SwitchItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    spec: TransformationSpec,
    secondaryLabel: String? = null,
    @DrawableRes icon: Int? = null,
) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier
            .fillMaxWidth()
            .transformedHeight(this, spec)
            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
        transformation = SurfaceTransformation(spec),
        icon = icon?.let { res -> { Icon(painterResource(res), contentDescription = null) } },
        secondaryLabel = secondaryLabel?.let { text -> { Text(text, maxLines = 3) } },
        label = { Label(label) },
    )
}

@Composable
fun TransformingLazyColumnItemScope.RadioItem(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    spec: TransformationSpec,
    secondaryLabel: String? = null,
) {
    RadioButton(
        selected = selected,
        onSelect = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .transformedHeight(this, spec)
            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
        transformation = SurfaceTransformation(spec),
        secondaryLabel = secondaryLabel?.let { text -> { Text(text, maxLines = 3, overflow = TextOverflow.Ellipsis) } },
        label = { Label(label) },
    )
}
