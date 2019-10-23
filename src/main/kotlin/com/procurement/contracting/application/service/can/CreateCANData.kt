package com.procurement.contracting.application.service.can

import com.procurement.contracting.domain.model.award.AwardId

data class CreateCANData(val award: Award?) {
    data class Award(val id: AwardId)
}
