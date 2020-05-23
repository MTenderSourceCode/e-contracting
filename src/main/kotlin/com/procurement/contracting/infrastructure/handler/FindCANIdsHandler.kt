package com.procurement.contracting.infrastructure.handler

import com.fasterxml.jackson.databind.JsonNode
import com.procurement.contracting.application.service.Logger
import com.procurement.contracting.application.service.can.CANService
import com.procurement.contracting.domain.functional.Result
import com.procurement.contracting.domain.functional.Result.Companion.failure
import com.procurement.contracting.domain.model.can.CANId
import com.procurement.contracting.infrastructure.converter.convert
import com.procurement.contracting.infrastructure.dto.can.find.FindCANIdsRequest
import com.procurement.contracting.infrastructure.fail.Fail
import com.procurement.contracting.model.dto.bpe.Command2Type
import com.procurement.contracting.model.dto.bpe.tryGetParams
import org.springframework.stereotype.Component

@Component
class FindCANIdsHandler(
    private val CANService: CANService, logger: Logger
) : AbstractHandler<Command2Type, List<CANId>>(logger) {

    override val action: Command2Type = Command2Type.FIND_CAN_IDS

    override fun execute(node: JsonNode): Result<List<CANId>, Fail> {
        val params = node
            .tryGetParams(FindCANIdsRequest::class.java)
            .doReturn { error -> return failure(error) }
            .convert()
            .doReturn { error -> return failure(error) }

        return CANService.findCANIds(params = params)
    }
}