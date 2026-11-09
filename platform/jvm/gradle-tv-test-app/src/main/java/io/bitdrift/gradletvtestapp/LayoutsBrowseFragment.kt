// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletvtestapp

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import io.bitdrift.capture.Capture

class LayoutsBrowseFragment : BrowseSupportFragment() {
    private lateinit var backgroundManager: BackgroundManager

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        backgroundManager = BackgroundManager.getInstance(requireActivity()).apply {
            attach(requireActivity().window)
        }

        setupUi()
        loadRows()
        onItemViewClickedListener = ItemClickListener()
        onItemViewSelectedListener = ItemSelectedListener()

        Capture.Logger.logInfo { "Leanback browse screen opened" }
    }

    private fun setupUi() {
        title = getString(R.string.layouts_examples_title)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = 0xFF111827.toInt()
        searchAffordanceColor = 0xFF60A5FA.toInt()
        backgroundManager.drawable = ColorDrawable(0xFF060B16.toInt())
    }

    private fun loadRows() {
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        LayoutExamplesCatalog.rows(requireContext()).forEachIndexed { index, row ->
            val rowAdapter = ArrayObjectAdapter(LayoutCardPresenter())
            row.items.forEach(rowAdapter::add)
            rowsAdapter.add(ListRow(HeaderItem(index.toLong(), row.title), rowAdapter))
        }
        adapter = rowsAdapter
    }

    private inner class ItemClickListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder?,
            item: Any?,
            rowViewHolder: RowPresenter.ViewHolder?,
            row: Row?,
        ) {
            val example = item as? LayoutExample ?: return
            Capture.Logger.logInfo(mapOf("pattern" to example.id, "screen" to "browse")) {
                "TV browse item selected"
            }
            startActivity(LayoutDetailsActivity.intent(requireContext(), example.id))
        }
    }

    private inner class ItemSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(
            itemViewHolder: Presenter.ViewHolder?,
            item: Any?,
            rowViewHolder: RowPresenter.ViewHolder?,
            row: Row?,
        ) {
            val example = item as? LayoutExample ?: return
            backgroundManager.drawable = ColorDrawable(example.accentColor)
        }
    }
}
