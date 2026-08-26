package com.filatelia.scanner.util

object CountryFlagHelper {
    fun getFlag(countryName: String?): String {
        if (countryName.isNullOrBlank()) return "🌐"
        val name = countryName.lowercase()
        return when {
            name.contains("rda") || name.contains("ddr") || name.contains("alemania oriental") -> "🇩🇩"
            name.contains("alemania") || name.contains("bundespost") || name.contains("germany") || name.contains("rfa") || name.contains("berlín") || name.contains("berlin") || name.contains("pfa") -> "🇩🇪"
            name.contains("méxico") || name.contains("mexico") -> "🇲🇽"
            name.contains("estados unidos") || name.contains("usa") || name.contains("united states") -> "🇺🇸"
            name.contains("españa") || name.contains("spain") -> "🇪🇸"
            name.contains("francia") || name.contains("france") -> "🇫🇷"
            name.contains("reino unido") || name.contains("gran bretaña") || name.contains("uk") -> "🇬🇧"
            name.contains("italia") || name.contains("italy") -> "🇮🇹"
            name.contains("canadá") || name.contains("canada") -> "🇨🇦"
            name.contains("japón") || name.contains("japan") -> "🇯🇵"
            name.contains("china") -> "🇨🇳"
            name.contains("rusia") || name.contains("urss") || name.contains("soviet") -> "🇷🇺"
            name.contains("argentina") -> "🇦🇷"
            name.contains("colombia") -> "🇨🇴"
            name.contains("chile") -> "🇨🇱"
            name.contains("brasil") || name.contains("brazil") -> "🇧🇷"
            name.contains("australia") -> "🇦🇺"
            name.contains("suiza") || name.contains("switzerland") || name.contains("helvetia") -> "🇨🇭"
            name.contains("austria") || name.contains("österreich") -> "🇦🇹"
            name.contains("bélgica") || name.contains("belgique") || name.contains("belgium") -> "🇧🇪"
            name.contains("países bajos") || name.contains("holanda") || name.contains("netherlands") -> "🇳🇱"
            name.contains("suecia") || name.contains("sweden") -> "🇸🇪"
            name.contains("noruega") || name.contains("norway") -> "🇳🇴"
            name.contains("dinamarca") || name.contains("denmark") -> "🇩🇰"
            name.contains("portugal") -> "🇵🇹"
            name.contains("cuba") -> "🇨🇺"
            name.contains("perú") || name.contains("peru") -> "🇵🇪"
            name.contains("venezuela") -> "🇻🇪"
            else -> "🌐"
        }
    }
}