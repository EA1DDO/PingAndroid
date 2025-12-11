package com.example.pingmonitor

import org.json.JSONObject

data class HostEntry(
    val ip: String,
    var active: Boolean,
    val history: MutableList<Double> = mutableListOf()
) {
    fun toJsonString(): String {
        val o = JSONObject()
        o.put("ip", ip)
        o.put("active", active)
        val arr = org.json.JSONArray()
        for (v in history) arr.put(v)
        o.put("history", arr)
        return o.toString()
    }

    companion object {
        fun fromJsonString(s: String): HostEntry? {
            return try {
                val o = JSONObject(s)
                val ip = o.getString("ip")
                val active = o.optBoolean("active", true)
                val hist = mutableListOf<Double>()
                val arr = o.optJSONArray("history")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        hist.add(arr.optDouble(i, 0.0))
                    }
                }
                HostEntry(ip, active, hist)
            } catch (e: Exception) {
                null
            }
        }
    }
}
