// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletvtestapp

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnActionClickedListener
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.leanback.widget.SparseArrayObjectAdapter
import io.bitdrift.capture.Capture

class LayoutDetailsFragment : DetailsSupportFragment() {
    private lateinit var example: LayoutExample

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val exampleId = LayoutDetailsActivity.exampleId(requireActivity().intent)
        example = LayoutExamplesCatalog.find(requireContext(), exampleId.orEmpty()) ?: run {
            requireActivity().finish()
            return
        }

        BackgroundManager.getInstance(requireActivity()).apply {
            attach(requireActivity().window)
            drawable = ColorDrawable(example.accentColor)
        }

        title = example.title
        adapter = buildAdapter()
        onItemViewClickedListener = RelatedItemClickListener()

        Capture.Logger.logInfo(mapOf("pattern" to example.id, "screen" to "details")) {
            "TV details screen opened"
        }
    }

    private fun buildAdapter(): ArrayObjectAdapter {
        val presenterSelector = ClassPresenterSelector()
        presenterSelector.addClassPresenter(
            DetailsOverviewRow::class.java,
            FullWidthDetailsOverviewRowPresenter(LayoutDescriptionPresenter()).apply {
                backgroundColor = example.accentColor
                isParticipatingEntranceTransition = false
                onActionClickedListener = DetailsActionClickListener()
            },
        )
        presenterSelector.addClassPresenter(ListRow::class.java, ListRowPresenter())

        return ArrayObjectAdapter(presenterSelector).apply {
            add(detailsOverviewRow())
            add(relatedRow())
        }
    }

    private fun detailsOverviewRow(): DetailsOverviewRow {
        val actions = SparseArrayObjectAdapter().apply {
            set(1, Action(1, getString(R.string.action_open_docs), getString(R.string.action_open_docs_subtitle)))
            set(2, Action(2, getString(R.string.action_log_selection), getString(R.string.action_log_selection_subtitle)))
            set(3, Action(3, getString(R.string.action_back_to_browse), ""))
        }

        return DetailsOverviewRow(example).apply {
            imageDrawable = ColorDrawable(example.accentColor)
            actionsAdapter = actions
        }
    }

    private fun relatedRow(): ListRow {
        val relatedItems = LayoutExamplesCatalog.related(requireContext(), example)
        val rowAdapter = ArrayObjectAdapter(LayoutCardPresenter())
        relatedItems.forEach(rowAdapter::add)
        return ListRow(HeaderItem(0, getString(R.string.related_patterns_title)), rowAdapter)
    }

    private inner class DetailsActionClickListener : OnActionClickedListener {
        override fun onActionClicked(action: Action) {
            when (action.id) {
                1L -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(example.docsUrl)))
                2L -> Capture.Logger.logInfo(mapOf("pattern" to example.id, "action" to "details_log_selection")) {
                    "TV pattern action clicked"
                }
                3L -> requireActivity().finish()
            }
        }
    }

    private inner class RelatedItemClickListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder?,
            item: Any?,
            rowViewHolder: RowPresenter.ViewHolder?,
            row: Row?,
        ) {
            val relatedExample = item as? LayoutExample ?: return
            startActivity(LayoutDetailsActivity.intent(requireContext(), relatedExample.id))
        }
    }

    private class LayoutDescriptionPresenter : AbstractDetailsDescriptionPresenter() {
        override fun onBindDescription(viewHolder: ViewHolder, item: Any) {
            val example = item as LayoutExample
            viewHolder.title.text = example.title
            viewHolder.subtitle.text = example.category
            viewHolder.body.text = example.body
        }
    }
}
