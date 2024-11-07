package com.example.scannerqrcode.model

import com.google.gson.annotations.SerializedName

data class ValidateResponse(

	@field:SerializedName("success")
	val success: Boolean? = null,

	@field:SerializedName("obj")
	val obj: Obj? = null,

	@field:SerializedName("status")
	val status: String? = null
)

data class Obj(

	@field:SerializedName("progress")
	val progress: String? = null,

	@field:SerializedName("username")
	val username: String? = null
)
