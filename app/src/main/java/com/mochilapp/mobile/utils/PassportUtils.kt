package com.mochilapp.mobile.utils

object PassportUtils {
    
    fun getLevelFromPoints(points: Long): String {
        return when {
            points >= 2000 -> "Viajero Legendario"
            points >= 1200 -> "Embajador Local"
            points >= 700 -> "Aventurero"
            points >= 300 -> "Mochilero"
            else -> "Explorador"
        }
    }

    fun getNextLevel(points: Long): String? {
        return when {
            points < 300 -> "Mochilero"
            points < 700 -> "Aventurero"
            points < 1200 -> "Embajador Local"
            points < 2000 -> "Viajero Legendario"
            else -> null
        }
    }

    fun getProgressToNextLevel(points: Long): Float {
        return when {
            points < 300 -> points.toFloat() / 300f
            points < 700 -> (points - 300).toFloat() / (700f - 300f)
            points < 1200 -> (points - 700).toFloat() / (1200f - 700f)
            points < 2000 -> (points - 1200).toFloat() / (2000f - 1200f)
            else -> 1.0f
        }
    }

    fun getPointsRemaining(points: Long): Long {
        return when {
            points < 300 -> 300 - points
            points < 700 -> 700 - points
            points < 1200 -> 1200 - points
            points < 2000 -> 2000 - points
            else -> 0
        }
    }
}
