package com.example.swipetorefresh.view

import android.content.Context
import android.os.Handler
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import com.example.swipetorefresh.R
import kotlinx.android.synthetic.main.pull_to_refresh.view.*

class PullToRefresh : RelativeLayout, LoadingView.OnRefreshListener {
    override fun onRefresh() {
        this.tvPullToRefresh.visibility = View.VISIBLE
        this.llLoading.visibility = View.GONE
    }

    override fun isLoading() {
        this.tvPullToRefresh.visibility = View.GONE
        this.llLoading.visibility = View.VISIBLE
        // TODO remove this
        Handler().postDelayed({
            this.pullToRefreshLayout.setRefreshing(false)
        }, 3000)
    }

    constructor(context: Context) : super(context, null) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs, 0) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    private fun init() {
        View.inflate(context, R.layout.pull_to_refresh, this)
        this.pullToRefreshLayout.setOnRefreshListener(this)
    }

    fun setLoading(boolean: Boolean) {
        this.pullToRefreshLayout.setRefreshing(boolean)
    }
}