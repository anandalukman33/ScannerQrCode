package com.example.scannerqrcode.model

import com.google.gson.annotations.SerializedName

data class UserListResponse(

	@field:SerializedName("username")
	val username: List<String>
)
