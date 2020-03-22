package org.ascolto.onlus.geocrowd19.android.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.ascolto.onlus.geocrowd19.android.models.survey.TriageProfileId
import org.ascolto.onlus.geocrowd19.android.models.survey.UserHealthState
import java.util.*

@JsonClass(generateAdapter = true)
data class UserHealthProfile(
    @field:Json(name = "user_id") val userId: String,
    @field:Json(name = "health_state") val healthState: UserHealthState,
    @field:Json(name = "triage_profile_id") val triageProfileId: TriageProfileId?,
    @field:Json(name = "lastSurveyVersion") val lastSurveyVersion: String?,
    @field:Json(name = "lastSurveyDate") val lastSurveyDate: Date?
) {
    val key = key(userId)

    companion object {
        fun key(userId: String) = "user_health_profile_$userId"
    }
}
