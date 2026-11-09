// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletvtestapp

import android.content.Context
import android.graphics.Color

data class LayoutExample(
    val id: String,
    val title: String,
    val summary: String,
    val body: String,
    val category: String,
    val cardWidthDp: Int,
    val cardHeightDp: Int,
    val accentColor: Int,
    val docsUrl: String,
)

data class LayoutExampleRow(
    val title: String,
    val items: List<LayoutExample>,
)

object LayoutExamplesCatalog {
    fun rows(context: Context): List<LayoutExampleRow> = listOf(
        LayoutExampleRow(
            title = context.getString(R.string.browse_section_title),
            items = listOf(
                example(
                    id = "browse",
                    title = context.getString(R.string.template_browse),
                    summary = context.getString(R.string.template_browse_desc),
                    body = context.getString(R.string.template_browse_body),
                    category = context.getString(R.string.browse_section_title),
                    cardWidthDp = 320,
                    cardHeightDp = 180,
                    accentColor = Color.parseColor("#1D4ED8"),
                    docsUrl = "https://developer.android.com/design/ui/tv/guides/styles/layouts",
                ),
                example(
                    id = "left-overlay",
                    title = context.getString(R.string.template_left_overlay),
                    summary = context.getString(R.string.template_left_overlay_desc),
                    body = context.getString(R.string.template_left_overlay_body),
                    category = context.getString(R.string.browse_section_title),
                    cardWidthDp = 320,
                    cardHeightDp = 180,
                    accentColor = Color.parseColor("#0F766E"),
                    docsUrl = "https://developer.android.com/design/ui/tv/guides/styles/layouts",
                ),
                example(
                    id = "right-overlay",
                    title = context.getString(R.string.template_right_overlay),
                    summary = context.getString(R.string.template_right_overlay_desc),
                    body = context.getString(R.string.template_right_overlay_body),
                    category = context.getString(R.string.browse_section_title),
                    cardWidthDp = 320,
                    cardHeightDp = 180,
                    accentColor = Color.parseColor("#7C3AED"),
                    docsUrl = "https://developer.android.com/design/ui/tv/guides/styles/layouts",
                ),
                example(
                    id = "center-overlay",
                    title = context.getString(R.string.template_center_overlay),
                    summary = context.getString(R.string.template_center_overlay_desc),
                    body = context.getString(R.string.template_center_overlay_body),
                    category = context.getString(R.string.browse_section_title),
                    cardWidthDp = 320,
                    cardHeightDp = 180,
                    accentColor = Color.parseColor("#C2410C"),
                    docsUrl = "https://developer.android.com/design/ui/tv/guides/styles/layouts",
                ),
                example(
                    id = "bottom-overlay",
                    title = context.getString(R.string.template_bottom_overlay),
                    summary = context.getString(R.string.template_bottom_overlay_desc),
                    body = context.getString(R.string.template_bottom_overlay_body),
                    category = context.getString(R.string.browse_section_title),
                    cardWidthDp = 320,
                    cardHeightDp = 180,
                    accentColor = Color.parseColor("#BE185D"),
                    docsUrl = "https://developer.android.com/design/ui/tv/guides/styles/layouts",
                ),
            ),
        ),
        LayoutExampleRow(
            title = context.getString(R.string.card_layouts_title),
            items = listOf(
                example(
                    id = "one-card",
                    title = context.getString(R.string.card_layout_1_title),
                    summary = context.getString(R.string.card_layout_1_summary),
                    body = context.getString(R.string.card_layout_1_body),
                    category = context.getString(R.string.card_layouts_title),
                    cardWidthDp = 844,
                    cardHeightDp = 250,
                    accentColor = Color.parseColor("#2563EB"),
                    docsUrl = "https://developer.android.com/design/ui/tv/guides/styles/layouts#1-card-layout",
                ),
                example(
                    id = "two-card",
                    title = context.getString(R.string.card_layout_2_title),
                    summary = context.getString(R.string.card_layout_2_summary),
                    body = context.getString(R.string.card_layout_2_body),
                    category = context.getString(R.string.card_layouts_title),
                    cardWidthDp = 412,
                    cardHeightDp = 250,
                    accentColor = Color.parseColor("#0F766E"),
                    docsUrl = "https://developer.android.com/design/ui/tv/guides/styles/layouts",
                ),
                example(
                    id = "three-card",
                    title = context.getString(R.string.card_layout_3_title),
                    summary = context.getString(R.string.card_layout_3_summary),
                    body = context.getString(R.string.card_layout_3_body),
                    category = context.getString(R.string.card_layouts_title),
                    cardWidthDp = 268,
                    cardHeightDp = 250,
                    accentColor = Color.parseColor("#7C3AED"),
                    docsUrl = "https://developer.android.com/design/ui/tv/guides/styles/layouts",
                ),
                example(
                    id = "four-card",
                    title = context.getString(R.string.card_layout_4_title),
                    summary = context.getString(R.string.card_layout_4_summary),
                    body = context.getString(R.string.card_layout_4_body),
                    category = context.getString(R.string.card_layouts_title),
                    cardWidthDp = 196,
                    cardHeightDp = 250,
                    accentColor = Color.parseColor("#C2410C"),
                    docsUrl = "https://developer.android.com/design/ui/tv/guides/styles/layouts",
                ),
                example(
                    id = "five-card",
                    title = context.getString(R.string.card_layout_5_title),
                    summary = context.getString(R.string.card_layout_5_summary),
                    body = context.getString(R.string.card_layout_5_body),
                    category = context.getString(R.string.card_layouts_title),
                    cardWidthDp = 152,
                    cardHeightDp = 250,
                    accentColor = Color.parseColor("#BE185D"),
                    docsUrl = "https://developer.android.com/design/ui/tv/guides/styles/layouts",
                ),
            ),
        ),
        LayoutExampleRow(
            title = context.getString(R.string.components_section_title),
            items = listOf(
                example(
                    id = "actions",
                    title = context.getString(R.string.components_actions_title),
                    summary = context.getString(R.string.components_actions_summary),
                    body = context.getString(R.string.components_actions_body),
                    category = context.getString(R.string.components_section_title),
                    cardWidthDp = 300,
                    cardHeightDp = 180,
                    accentColor = Color.parseColor("#0369A1"),
                    docsUrl = "https://developer.android.com/design/ui/tv/guides/components",
                ),
                example(
                    id = "tabs",
                    title = context.getString(R.string.components_tabs_title),
                    summary = context.getString(R.string.components_tabs_summary),
                    body = context.getString(R.string.components_tabs_body),
                    category = context.getString(R.string.components_section_title),
                    cardWidthDp = 300,
                    cardHeightDp = 180,
                    accentColor = Color.parseColor("#166534"),
                    docsUrl = "https://developer.android.com/design/ui/tv/guides/components",
                ),
                example(
                    id = "dialogs",
                    title = context.getString(R.string.components_dialogs_title),
                    summary = context.getString(R.string.components_dialogs_summary),
                    body = context.getString(R.string.components_dialogs_body),
                    category = context.getString(R.string.components_section_title),
                    cardWidthDp = 300,
                    cardHeightDp = 180,
                    accentColor = Color.parseColor("#7C2D12"),
                    docsUrl = "https://developer.android.com/design/ui/tv/guides/components",
                ),
                example(
                    id = "navigation",
                    title = context.getString(R.string.components_navigation_title),
                    summary = context.getString(R.string.components_navigation_summary),
                    body = context.getString(R.string.components_navigation_body),
                    category = context.getString(R.string.components_section_title),
                    cardWidthDp = 300,
                    cardHeightDp = 180,
                    accentColor = Color.parseColor("#4338CA"),
                    docsUrl = "https://developer.android.com/design/ui/tv/guides/components",
                ),
            ),
        ),
    )

    fun find(context: Context, id: String): LayoutExample? = rows(context)
        .flatMap { it.items }
        .firstOrNull { it.id == id }

    fun related(context: Context, example: LayoutExample): List<LayoutExample> = rows(context)
        .flatMap { it.items }
        .filter { it.category == example.category && it.id != example.id }

    private fun example(
        id: String,
        title: String,
        summary: String,
        body: String,
        category: String,
        cardWidthDp: Int,
        cardHeightDp: Int,
        accentColor: Int,
        docsUrl: String,
    ): LayoutExample = LayoutExample(
        id = id,
        title = title,
        summary = summary,
        body = body,
        category = category,
        cardWidthDp = cardWidthDp,
        cardHeightDp = cardHeightDp,
        accentColor = accentColor,
        docsUrl = docsUrl,
    )
}
