package com.example.anprsystemsltd.androidlibrary

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.TextView
import com.anpr.sdk.mobile.j2ni.JavaToNative
import kotlinx.android.synthetic.main.dialog_info.*

class InfoDialog(
    context: Context,
    private val closeListener: CloseListener,
    private val parameters: Parameter
) : Dialog(context) {

    interface CloseListener {
        fun onClosed()
    }

    private var width = 0
    private var height = 0
    private var textSizeLarge = 0f
    private var textSizeNormal = 0f
    private var textSizeSmall = 0f

    private lateinit var logListAdapter: LogListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(false)
        setCanceledOnTouchOutside(false)

        setContentView(R.layout.dialog_info)

        calculateSize()

        setUISize()

        iwClose.setOnClickListener { close() }

        lwLogs.adapter = logListAdapter




        setData()


    }




    private fun calculateSize() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        DisplayMetrics().apply {
            display.getMetrics(this)
            width = (widthPixels * 0.9).toInt()
            height = (heightPixels * 0.9).toInt()
            textSizeLarge = (height * 0.02).toFloat()
            textSizeNormal = (textSizeLarge * 0.8).toFloat()
            textSizeSmall = (textSizeNormal * 0.7).toFloat()
        }
    }

    private fun setUISize() {
        window?.setLayout(width, height)
        twTitle.textSize = textSizeLarge
        iwClose.layoutParams.width = (textSizeLarge * 3).toInt()
        iwClose.layoutParams.height = (textSizeLarge * 3).toInt()
        twLabelVersion.textSize = textSizeNormal
        twDataVersion.textSize = textSizeNormal
        twLabelHardwareId.textSize = textSizeNormal
        twDataHardwareId.textSize = textSizeNormal
        twLabelAndroidId.textSize = textSizeNormal
        twDataAndroidId.textSize = textSizeNormal
        twLabelLicensServer.textSize = textSizeNormal
        twDataLicensServer.textSize = textSizeNormal
        twLogListHeader.textSize = textSizeNormal
        logListAdapter = LogListAdapter(context, textSizeSmall)
    }

    private fun setData() {
        twDataVersion.text = parameters.libraryInterface.libraryVersion
        twDataHardwareId.text = parameters.libraryInterface.hardwareIdByLibrary
        twDataAndroidId.text = parameters.libraryInterface.androidIdByLibrary
        twDataLicensServer.text = if (!parameters.licenceOnServer) {
            "Not found "
        } else {
            "Found "
        } + "for ${parameters.deviceId}"
        logListAdapter.addAll(parameters.libraryInterface.allLogs)
    }

    private fun close() {
        closeListener.onClosed()
        cancel()
    }

    data class Parameter (
        var libraryInterface: JavaToNative,
        var deviceId: String,
        var licenceOnServer: Boolean
    )

    class LogListAdapter(context: Context, private val textSize: Float) : ArrayAdapter<String>(context, R.layout.adapter_loglist) {

        private val items = ArrayList<String>()
        private val inflater = LayoutInflater.from(context)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val itemLayout = inflater.inflate(R.layout.adapter_loglist, parent, false) as FrameLayout
            val textView = itemLayout.findViewById<TextView>(R.id.twItem)
            textView.textSize = textSize
            textView.setTextColor(Color.BLACK)
            textView.text = getItem(position)
            itemLayout.layoutParams.height = (textSize * 3.5).toInt()
            return itemLayout
        }


    }

}