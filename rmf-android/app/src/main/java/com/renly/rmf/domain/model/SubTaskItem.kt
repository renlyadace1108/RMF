package com.renly.rmf.domain.model

data class SubTaskItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val isDone: Boolean = false
) {
    fun toJson(): String {
        val escapedTitle = title.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """{"id":"$id","title":"$escapedTitle","isDone":$isDone}"""
    }

    companion object {
        fun listToJson(items: List<SubTaskItem>): String {
            return "[" + items.joinToString(",") { it.toJson() } + "]"
        }

        fun parseList(json: String?): List<SubTaskItem> {
            if (json.isNullOrBlank() || json.trim() == "[]") return emptyList()
            val list = mutableListOf<SubTaskItem>()
            try {
                val array = org.json.JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        SubTaskItem(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            title = obj.optString("title", ""),
                            isDone = obj.optBoolean("isDone", false)
                        )
                    )
                }
            } catch (_: Exception) {}
            return list
        }
    }
}
