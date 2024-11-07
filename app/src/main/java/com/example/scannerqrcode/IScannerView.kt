package com.example.scannerqrcode

import com.example.scannerqrcode.model.ValidateResponse

interface IScannerView {
    fun loading(status: Boolean, message: String)
    fun error(message: String)
    fun validateSuccess(data: ValidateResponse)
    fun errorApi(message: String)
    fun loadingUserList(status: Boolean)
    fun successUserList(data: List<String>)
}