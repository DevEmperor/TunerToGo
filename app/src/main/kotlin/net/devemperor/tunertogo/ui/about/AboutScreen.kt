package net.devemperor.tunertogo.ui.about

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import net.devemperor.tunertogo.BuildConfig
import net.devemperor.tunertogo.R
import net.devemperor.tunertogo.ui.components.Header
import net.devemperor.tunertogo.ui.components.ListScreen

@Composable
fun AboutScreen() {
    ListScreen { spec ->
        item {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_monochrome),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp).transformedHeight(this, spec),
            )
        }
        item { Header(stringResource(R.string.app_title), spec) }
        item { Paragraph(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), MaterialTheme.colorScheme.onSurface) }
        item { Paragraph(stringResource(R.string.about_author)) }
        item { Paragraph(stringResource(R.string.about_privacy)) }
        item { Paragraph(stringResource(R.string.about_license)) }
        item { Paragraph(stringResource(R.string.about_source), MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun Paragraph(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}
