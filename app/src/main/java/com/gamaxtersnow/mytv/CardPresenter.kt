package com.gamaxtersnow.mytv

import android.content.Context
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.ImageView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.gamaxtersnow.mytv.models.TVViewModel

class CardPresenter(
    private val context: Context,
) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = object :
            ImageCardView(ContextThemeWrapper(parent.context, R.style.CustomImageCardTheme)) {}

        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true

        // Default state
        cardView.setBackgroundColor(COLOR_DEFAULT)
        cardView.setInfoAreaBackgroundColor(COLOR_DEFAULT)

        // Focus highlight: lighter background when selected
        cardView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                cardView.setBackgroundColor(COLOR_FOCUSED)
                cardView.setInfoAreaBackgroundColor(COLOR_FOCUSED)
            } else {
                cardView.setBackgroundColor(COLOR_DEFAULT)
                cardView.setInfoAreaBackgroundColor(COLOR_DEFAULT)
            }
        }

        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val tvViewModel = item as TVViewModel
        val cardView = viewHolder.view as ImageCardView

        cardView.titleText = tvViewModel.getTV().title
        cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
        cardView.tag = tvViewModel.videoUrl.value

        cardView.mainImageView?.let {
            Glide.with(viewHolder.view.context)
                .load(tvViewModel.getTV().logo)
                .centerInside()
                .into(it)
        }

        cardView.setMainImageScaleType(ImageView.ScaleType.CENTER_INSIDE)

        val epg = tvViewModel.epg.value?.filter { it.beginTime < Utils.getDateTimestamp() }
        if (!epg.isNullOrEmpty()) {
            cardView.contentText = epg.last().title
        } else {
            cardView.contentText = ""
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.mainImage = null
    }

    companion object {
        private const val TAG = "CardPresenter"
        private const val CARD_WIDTH = 200
        private const val CARD_HEIGHT = 150

        private val COLOR_DEFAULT = Color.parseColor("#FF263238")
        private val COLOR_FOCUSED = Color.parseColor("#FF455A64")
    }
}
