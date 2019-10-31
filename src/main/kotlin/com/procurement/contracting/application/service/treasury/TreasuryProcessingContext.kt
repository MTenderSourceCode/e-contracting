package com.procurement.contracting.application.service.treasury

import java.time.LocalDateTime

data class TreasuryProcessingContext(
    val cpid: String,
    val ocid: String,
    val startDate: LocalDateTime
)