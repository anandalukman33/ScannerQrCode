package com.example.scannerqrcode

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.text.HtmlCompat

class CustomButtonDialog (context: Context, themeResId: Int) : Dialog(context, themeResId) {

    private var mOkBtn: Button? = null
    private var mCancelBtn: Button? = null
    private var mContentTxt: TextView? = null
    private var mContent: String? = null
    private var mTitleTxt: TextView? = null
    private var mTitle: String? = null
    private var mOkText: String? = null
    private var mCancelText: String? = null
    private var mOnBtnClickListener: OnBtnClickListener? = null
    private var isDoubleBtn: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.single_dialog_button)
        initUI()
        initListener()
        updateData()
    }

    private fun initUI() {
        mOkBtn = findViewById(R.id.btn_ok)
        mContentTxt = findViewById(R.id.txt_content)
        mContentTxt?.movementMethod = ScrollingMovementMethod()
        mTitleTxt = findViewById(R.id.txt_title_warning)
        mCancelBtn = findViewById(R.id.c_btn_cancel)
    }

    private fun updateData() {
        mTitleTxt.let {
            mTitleTxt?.text = mTitle
        }
        mContentTxt.let {
            mContentTxt?.text = HtmlCompat.fromHtml(mContent.toString(), 0)
        }
        if (isDoubleBtn != null) {
            if (isDoubleBtn!!) {
                mCancelBtn?.visibility = View.VISIBLE
            } else {
                mCancelBtn?.visibility = View.GONE
            }
        }
        if (mOkText != null) {
            mOkBtn.let {
                mOkBtn?.text = mOkText
            }
        }
        if (mCancelText != null) {
            mCancelBtn.let {
                mCancelBtn?.text = mCancelText
            }
        }
    }



    fun setButtonOkText(rightText: String) {
        mOkText = rightText
    }

    fun setButtonCancelText(value: String) {
        mCancelText = value
    }

    fun isDoubleBtn(value: Boolean) {
        isDoubleBtn = value
    }

    private fun initListener() {
        mOkBtn.let {
            it?.setOnClickListener { v ->
                mOnBtnClickListener.let {
                    mOnBtnClickListener?.okBtnClick(v!!)
                }
            }
        }

        mCancelBtn.let {
            it?.setOnClickListener { v ->
                mOnBtnClickListener.let {
                    mOnBtnClickListener?.cancelBtnClick(v!!)
                }
            }
        }
    }

    fun setOnBtnClickListener(listener: OnBtnClickListener) {
        mOnBtnClickListener = listener
    }

    fun setDialogTitle(content: String) {
        mTitle = content
    }

    fun setDialogContent(content: String) {
        mContent = content
    }

    override fun show() {
        if (isShowing) {
            dismiss()
        }
        updateData()
        super.show()
    }

}

interface OnBtnClickListener {
    fun okBtnClick(view: View)

    fun cancelBtnClick(view: View)
}