package com.example.runnerapp.utils

import kotlin.math.max
import kotlin.math.pow

object CalorieCalculator {
    
    /**
     * Calculate calories burned during exercise using multiple factors
     * Based on MET (Metabolic Equivalent of Task) values and user profile
     */
    fun calculateCaloriesBurned(
        sport: String,
        durationMinutes: Double,
        distanceKm: Double,
        steps: Int,
        userWeight: Float,
        userAge: Int,
        userGender: String,
        userHeight: Float
    ): Double {
        
        // Calculate BMR (Basal Metabolic Rate) using Mifflin-St Jeor Equation
        val bmr = calculateBMR(userWeight, userHeight, userAge, userGender)
        
        // Get MET value based on sport and intensity
        val metValue = getMETValue(sport, distanceKm, durationMinutes, steps)
        
        // Calculate calories: (MET * weight in kg * duration in hours)
        val durationHours = durationMinutes / 60.0
        val baseCalories = metValue * userWeight * durationHours
        
        // Apply gender and age adjustments
        val genderMultiplier = when (userGender.lowercase()) {
            "masculino" -> 1.0
            "femenino" -> 0.9
            else -> 0.95
        }
        
        val ageMultiplier = when {
            userAge < 25 -> 1.05
            userAge < 35 -> 1.0
            userAge < 50 -> 0.95
            else -> 0.9
        }
        
        return max(baseCalories * genderMultiplier * ageMultiplier, 0.0)
    }
    
    /**
     * Calculate real-time calories burned during ongoing exercise
     */
    fun calculateRealTimeCalories(
        sport: String,
        elapsedSeconds: Int,
        currentDistanceKm: Double,
        currentSteps: Int,
        userWeight: Float,
        userAge: Int,
        userGender: String,
        userHeight: Float
    ): Double {
        val elapsedMinutes = elapsedSeconds / 60.0
        if (elapsedMinutes < 0.5) return 0.0 // Don't calculate for very short durations
        
        return calculateCaloriesBurned(
            sport, elapsedMinutes, currentDistanceKm, currentSteps,
            userWeight, userAge, userGender, userHeight
        )
    }
    
    private fun calculateBMR(weight: Float, height: Float, age: Int, gender: String): Double {
        return when (gender.lowercase()) {
            "masculino" -> 88.362 + (13.397 * weight) + (4.799 * height) - (5.677 * age)
            "femenino" -> 447.593 + (9.247 * weight) + (3.098 * height) - (4.330 * age)
            else -> 667.978 + (11.322 * weight) + (3.949 * height) - (5.004 * age) // Average
        }
    }
    
    private fun getMETValue(sport: String, distanceKm: Double, durationMinutes: Double, steps: Int): Double {
        val avgSpeedKmh = if (durationMinutes > 0) (distanceKm / durationMinutes) * 60 else 0.0
        val stepsPerMinute = if (durationMinutes > 0) steps / durationMinutes else 0.0
        
        return when (sport.uppercase()) {
            "RUN" -> {
                when {
                    avgSpeedKmh < 6.4 -> 6.0  // Light jogging
                    avgSpeedKmh < 8.0 -> 8.3  // Running 5 mph
                    avgSpeedKmh < 9.7 -> 9.8  // Running 6 mph
                    avgSpeedKmh < 11.3 -> 11.0 // Running 7 mph
                    avgSpeedKmh < 12.9 -> 11.8 // Running 8 mph
                    avgSpeedKmh < 14.5 -> 12.8 // Running 9 mph
                    avgSpeedKmh < 16.1 -> 14.5 // Running 10 mph
                    else -> 16.0 // Fast running
                }
            }
            "BIKE" -> {
                when {
                    avgSpeedKmh < 16.1 -> 4.0  // Leisurely cycling
                    avgSpeedKmh < 19.3 -> 6.8  // Moderate cycling
                    avgSpeedKmh < 22.5 -> 8.0  // Vigorous cycling
                    avgSpeedKmh < 25.7 -> 10.0 // Racing cycling
                    else -> 12.0 // Very fast cycling
                }
            }
            "SKATE" -> {
                when {
                    avgSpeedKmh < 13.0 -> 7.0  // Recreational skating
                    avgSpeedKmh < 17.0 -> 9.0  // Moderate skating
                    avgSpeedKmh < 21.0 -> 11.0 // Fast skating
                    else -> 13.0 // Racing skating
                }
            }
            else -> 6.0 // Default moderate activity
        }
    }
    
    /**
     * Get estimated calories per minute for real-time display
     */
    fun getCaloriesPerMinute(
        sport: String,
        userWeight: Float,
        userAge: Int,
        userGender: String,
        userHeight: Float,
        currentSpeedKmh: Double = 0.0
    ): Double {
        val estimatedMET = when (sport.uppercase()) {
            "RUN" -> when {
                currentSpeedKmh < 6.4 -> 6.0
                currentSpeedKmh < 8.0 -> 8.3
                currentSpeedKmh < 9.7 -> 9.8
                currentSpeedKmh < 11.3 -> 11.0
                else -> 12.0
            }
            "BIKE" -> when {
                currentSpeedKmh < 16.1 -> 4.0
                currentSpeedKmh < 19.3 -> 6.8
                currentSpeedKmh < 22.5 -> 8.0
                else -> 10.0
            }
            "SKATE" -> when {
                currentSpeedKmh < 13.0 -> 7.0
                currentSpeedKmh < 17.0 -> 9.0
                else -> 11.0
            }
            else -> 6.0
        }
        
        val genderMultiplier = when (userGender.lowercase()) {
            "masculino" -> 1.0
            "femenino" -> 0.9
            else -> 0.95
        }
        
        val ageMultiplier = when {
            userAge < 25 -> 1.05
            userAge < 35 -> 1.0
            userAge < 50 -> 0.95
            else -> 0.9
        }
        
        return (estimatedMET * userWeight / 60.0) * genderMultiplier * ageMultiplier
    }
}
