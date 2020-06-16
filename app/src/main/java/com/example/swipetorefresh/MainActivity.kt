package com.example.swipetorefresh

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.pull_to_refresh.view.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        pullToRefresh.recyclerView.layoutManager = LinearLayoutManager(this)
        pullToRefresh.recyclerView.adapter = RVAdapter()
        (pullToRefresh.recyclerView.adapter as RVAdapter).notifyDataSetChanged()

        btnLoad.setOnClickListener {
            pullToRefresh.setLoading(true)
        }

    }
}
