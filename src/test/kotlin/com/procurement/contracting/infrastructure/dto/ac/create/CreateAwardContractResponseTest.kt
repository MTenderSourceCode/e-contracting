package com.procurement.contracting.infrastructure.dto.ac.create

import com.procurement.contracting.infrastructure.AbstractDTOTestBase
import com.procurement.contracting.infrastructure.handler.v2.model.response.CreateAwardContractResponse
import org.junit.jupiter.api.Test

class CreateAwardContractResponseTest : AbstractDTOTestBase<CreateAwardContractResponse>(CreateAwardContractResponse::class.java) {

    @Test
    fun fully() {
        testBindingAndMapping("json/dto/ac/create/response/response_create_ac_fully.json")
    }

    @Test
    fun required1() {
        testBindingAndMapping("json/dto/ac/create/response/response_create_ac_required_1.json")
    }

    @Test
    fun required2() {
        testBindingAndMapping("json/dto/ac/create/response/response_create_ac_required_2.json")
    }
}
