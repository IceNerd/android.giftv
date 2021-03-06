package com.icenerd.giftv

import android.app.LoaderManager
import android.content.Context
import android.content.Intent
import android.content.Loader
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.support.v4.content.res.ResourcesCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.icenerd.adapter.RecyclerPageListener
import com.icenerd.giftv.data.loader.GIPHYSearchLoader
import com.icenerd.giftv.data.loader.GIPHYTrendingLoader
import com.icenerd.giftv.data.model.TCPServiceModel
import com.icenerd.giftv.fragment.dialog.AboutDialog
import com.icenerd.giftv.fragment.dialog.MobileTVNameDialog
import com.icenerd.giftv.fragment.dialog.SendChannelDialog
import com.icenerd.giftv.net.GIPHYService
import com.icenerd.giftv.net.Server
import com.icenerd.giphy.data.adapter.GifAdapter
import com.icenerd.giphy.data.model.GifModel
import com.icenerd.giphy.data.orm.GifORM
import org.json.JSONArray
import org.json.JSONException
import java.util.*

class MobileActivity : AppCompatActivity(), LoaderManager.LoaderCallbacks<Cursor>, ActionMode.Callback {
    companion object {
        private const val TAG = "MobileActivity"
        private val LOADER_ID_TRENDING = Random().nextInt()
        private val LOADER_ID_SEARCH = Random().nextInt()
    }

    private var currentSearchTerms: String? = null

    private var inputSearch: EditText? = null
    private var recyclerView: RecyclerView? = null

    private var loadMoreListener: RecyclerPageListener? = null
    private var gifAdapter: GifAdapter? = null

    private var abouDialog: AboutDialog? = null
    private var mobileTVNameDialog: MobileTVNameDialog? = null
    private var actionModeCurrent: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        inputSearch = findViewById(R.id.search_input)
        recyclerView = findViewById(R.id.recycler_view)

        loaderManager.initLoader(LOADER_ID_TRENDING, Bundle(), this).forceLoad()
    }

    override fun onStart() {
        super.onStart()

        val manager = GridLayoutManager(recyclerView?.context, 2)
        recyclerView?.layoutManager = manager
        recyclerView?.itemAnimator = DefaultItemAnimator()
        loadMoreListener = object : RecyclerPageListener(manager) {
            override fun onLoadMore(current_page: Int) {
                if(BuildConfig.DEBUG) Log.d("onLoadMore", "${current_page}")
                if (currentSearchTerms == null) {
                    val intent = Intent(GIPHYService.ACTION_GET_TRENDING, null, this@MobileActivity, GIPHYService::class.java)
                    if (Build.VERSION.SDK_INT >= 21) {
                        GIPHYService.enqueueWork(this@MobileActivity, intent)
                    } else {
                        startService(intent)
                    }
                } else {
                    val intent = Intent(GIPHYService.ACTION_GET_SEARCH, null, this@MobileActivity, GIPHYService::class.java)
                    intent.putExtra("terms", currentSearchTerms)
                    intent.putExtra("offset", current_page * GIPHYService.PAGESIZE_SEARCH)
                    if (Build.VERSION.SDK_INT >= 21) {
                        GIPHYService.enqueueWork(this@MobileActivity, intent)
                    } else {
                        startService(intent)
                    }
                }
            }
        }
        recyclerView?.addOnScrollListener(loadMoreListener)

        gifAdapter = GifAdapter()
        gifAdapter?.actionListener = object: GifAdapter.ActionListener {
            override fun onItemClick(model: GifModel) {
                if (BuildConfig.DEBUG) Log.d(TAG, "item clicked")
                if (actionModeCurrent == null) {
                    // nothing to do
                } else {
                    val count = gifAdapter?.selectedCount?:0
                    actionModeCurrent?.title = "$count GIF selected"
                }
            }
            override fun onItemLongClick(model: GifModel) {
                if (BuildConfig.DEBUG) Log.d(TAG, "item long clicked")
                if (actionModeCurrent == null) {
                    startSupportActionMode(this@MobileActivity)
                    gifAdapter?.toggleSelection = true
                } else {
                    // nothing to do
                }
            }
        }
        recyclerView?.adapter = gifAdapter
        findViewById<View>(R.id.action_search).setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(inputSearch?.windowToken, 0)
            if (actionModeCurrent == null && !inputSearch?.text.toString().isEmpty()) {
                ACTION_search(inputSearch?.text.toString())
            }
        }
        inputSearch?.imeOptions = EditorInfo.IME_ACTION_SEARCH
        inputSearch?.setOnEditorActionListener { textView, action_id, _ ->
            if (action_id == EditorInfo.IME_ACTION_SEARCH) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(textView.windowToken, 0)
                if (actionModeCurrent == null && !inputSearch?.text.toString().isEmpty()) {
                    ACTION_search(inputSearch?.text.toString())
                }
            }
            false
        }

        val sharedPrefs = getSharedPreferences("giftv", Context.MODE_PRIVATE)
        if (!sharedPrefs.contains("tutorial_about")) {
            val editor = sharedPrefs.edit()
            editor.putBoolean("tutorial_about", true)
            editor.apply()
            val fragMan = supportFragmentManager
            val ft = fragMan.beginTransaction()

            abouDialog = AboutDialog()
            abouDialog?.show(ft, "about_dialog")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.home, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_local_activity -> {
                startMobileTV()
                true
            }

            R.id.action_about -> {
                val ft = supportFragmentManager.beginTransaction()
                abouDialog = AboutDialog()
                abouDialog?.show(ft, "about_dialog")
                true
            }
            else -> false
        }
    }

    private fun startMobileTV() {
        val ft = supportFragmentManager.beginTransaction()

        mobileTVNameDialog = MobileTVNameDialog()
        mobileTVNameDialog?.actionListener = object : MobileTVNameDialog.ActionListener {
            override fun onStartMobileTV(name: String) {
                startMobileTV(name)
            }
        }
        mobileTVNameDialog?.show(ft, "mobile_tv_name_dialog")
    }

    private fun startMobileTV(name: String?) {
        if (name != null && !name.isEmpty()) {
            val sharedPref = getSharedPreferences("giftv", Context.MODE_PRIVATE)
            val editor = sharedPref.edit()
            editor.putString(MobileTVActivity.EXTRA_NAME, name)
            editor.apply()
            val intent = Intent(this, MobileTVActivity::class.java)
            intent.putExtra(Server.NAME, name)
            intent.putExtra(Server.TYPE, getString(R.string.network_service_type))
            startActivity(intent)
        }
    }

    private fun ACTION_search(query: String) {
        currentSearchTerms = query
        loaderManager.restartLoader(LOADER_ID_SEARCH, Bundle(), this).forceLoad()
        loadMoreListener?.onRefresh()
    }

    override fun onCreateLoader(id: Int, args: Bundle): Loader<Cursor>? {
        if (currentSearchTerms == null) {
            if (id == LOADER_ID_TRENDING) return GIPHYTrendingLoader(this)
        } else {
            if (id == LOADER_ID_SEARCH) return GIPHYSearchLoader(this, currentSearchTerms!!)
        }
        return null
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor) {
        gifAdapter?.swapCursor(data)
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        gifAdapter?.changeCursor(null)
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.actionmode_multi_select, menu)
        actionModeCurrent = mode
        inputSearch?.isEnabled = false
        if (Build.VERSION.SDK_INT >= 23) {
            inputSearch?.setTextColor(getColor(R.color.black_overlay))
        } else {
            inputSearch?.setTextColor(ResourcesCompat.getColor(resources, R.color.black_overlay, theme))
        }
        findViewById<View>(R.id.action_search).setBackgroundResource(R.color.colorPrimaryDark)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        return false
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId == R.id.action_send) {
            if (BuildConfig.DEBUG) Log.d(TAG, "action_send")

            val selectedGif = gifAdapter?.selectedGIF
            if (selectedGif != null && selectedGif.size > 0) {
                val fragMan = supportFragmentManager
                val dialog = SendChannelDialog()

                dialog.actionListener = object : SendChannelDialog.ActionListener {
                    override fun onTVSelected(model: TCPServiceModel) {
                        mode.finish()
                    }
                }

                var jsonBatch: String? = null
                try {
                    val json = JSONArray()
                    for (i in selectedGif.indices) {
                        json.put(selectedGif[i].getJSONObject())
                    }
                    jsonBatch = json.toString()
                } catch (err: JSONException) {
                    err.printStackTrace()
                }

                val args = Bundle()
                args.putString(GifORM.TABLE, jsonBatch)
                args.putString("terms", currentSearchTerms)
                dialog.arguments = args
                dialog.show(fragMan, "send_gif_batch")
            }
        }
        return false
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        actionModeCurrent = null
        gifAdapter?.clearSelections()
        inputSearch?.isEnabled = true
        if (Build.VERSION.SDK_INT >= 23) {
            inputSearch?.setTextColor(getColor(R.color.colorPrimary))
        } else {
            inputSearch?.setTextColor(ResourcesCompat.getColor(resources,R.color.colorPrimary,theme))
        }
        findViewById<View>(R.id.action_search).setBackgroundResource(R.color.colorAccent)
    }
}
