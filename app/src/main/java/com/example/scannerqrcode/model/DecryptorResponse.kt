package com.example.scannerqrcode.model

import com.google.gson.annotations.SerializedName

data class DecryptorResponse(

	@field:SerializedName("date")
	val date: String? = null,

	@field:SerializedName("progress")
	val progress: String? = null,

	@field:SerializedName("status")
	val status: Boolean? = null
)
