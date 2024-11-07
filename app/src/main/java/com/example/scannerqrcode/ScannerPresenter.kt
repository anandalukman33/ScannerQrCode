package com.example.scannerqrcode

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ScannerPresenter(
    private val view: IScannerView,
    private val api: ApiService
) {

    init {
        getUserList()
    }

    private fun getUserList() {
        view.loadingUserList(true)

        val coroutineHandler = CoroutineExceptionHandler { _, exception ->
            Log.e("coroutineHandler", "Error when getUserList : ${exception.localizedMessage}")
            view.loading(false, "")
            exception.localizedMessage?.let { view.error(it) }
        }

        CoroutineScope(Dispatchers.IO).launch(coroutineHandler) {
            val response = api.getUserList()
            if (response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    view.successUserList(response.body()!!.username)
                    view.loadingUserList(false)
                }
            } else {
                view.errorApi("error")
                view.loading(false, "")
            }
        }

    }

    fun decryptCode(code: String, username: String) {
        view.loading(true, "Decrypting Code...")

        val coroutineHandler = CoroutineExceptionHandler { _, exception ->
            Log.e("coroutineHandler", "Error when decryptCode : ${exception.localizedMessage}")
            view.loading(false, "")
            exception.localizedMessage?.let { view.error(it) }
        }
        CoroutineScope(Dispatchers.IO).launch(coroutineHandler) {
            val jsonObject = JSONObject()
            jsonObject.put("scanCode", code)

            val jsonObjectString = jsonObject.toString()
            val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())
            val response = api.decryptor(requestBody)

            if (response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    validateScanCode(username, response.body()!!.progress!!)
                }
            } else {
                view.errorApi("error")
                view.loading(false, "")
            }
        }
    }

    private fun validateScanCode(username: String, progress: String) {
        view.loading(true, "Validate Code...")
        val coroutineHandler = CoroutineExceptionHandler { _, exception ->
            Log.e("coroutineHandler", "Error when validateScanCode : ${exception.localizedMessage}")
            view.loading(false, "")
            exception.localizedMessage?.let { view.error(it) }
        }
        CoroutineScope(Dispatchers.IO).launch(coroutineHandler) {
            val jsonObject = JSONObject()
            jsonObject.put("username", username)
            jsonObject.put("progress", progress)

            val jsonObjectString = jsonObject.toString()
            val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())
            val response = api.validator(requestBody)

            if (response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    view.validateSuccess(response.body()!!)
//                    view.loading(false, "")
                }
            } else {
                view.errorApi("error")
                view.loading(false, "")
            }
        }
    }
}