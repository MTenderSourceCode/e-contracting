package com.procurement.contracting.model.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.procurement.contracting.model.dto.databinding.MoneyDeserializer
import com.procurement.contracting.model.dto.databinding.QuantityDeserializer
import com.procurement.contracting.model.dto.ocds.*
import java.math.BigDecimal
import java.util.*

data class UpdateAcRq @JsonCreator constructor(

        val award: AwardUpdate,

        val contract: ContractUpdate,

        val planning: Planning,

        val buyer: OrganizationReferenceBuyer,

        val funders: HashSet<OrganizationReference>?,

        val payers: HashSet<OrganizationReference>?,

        val treasuryBudgetSources: List<TreasuryBudgetSource>?
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AwardUpdate @JsonCreator constructor(

        val id: String,

        var value: ValueUpdate,

        var items: List<ItemUpdate>,

        var documents: List<DocumentAward>?,

        var suppliers: List<OrganizationReferenceSupplierUpdate>
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ContractUpdate @JsonCreator constructor(

        val title: String,

        val description: String,

        val period: Period,

        val documents: List<DocumentContract>,

        val milestones: List<Milestone>,

        val confirmationRequests: List<ConfirmationRequest>,

        val agreedMetrics: LinkedList<AgreedMetric>
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ItemUpdate @JsonCreator constructor(

        val id: String,

        val description: String?,

        val classification: Classification,

        val additionalClassifications: Set<Classification>?,

        @JsonDeserialize(using = QuantityDeserializer::class)
        val quantity: BigDecimal,

        val unit: UnitUpdate,

        val relatedLot: String,

        val deliveryAddress: Address
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UnitUpdate @JsonCreator constructor(

        val id: String,

        val name: String,

        val value: ValueUpdate
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ValueUpdate @JsonCreator constructor(

        @JsonDeserialize(using = MoneyDeserializer::class)
        val amount: BigDecimal,

        val currency: String,

        @JsonDeserialize(using = MoneyDeserializer::class)
        val amountNet: BigDecimal,

        val valueAddedTaxIncluded: Boolean
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TreasuryBudgetSource @JsonCreator constructor(

        var budgetBreakdownID: String,

        val budgetIBAN: String,

        @JsonDeserialize(using = MoneyDeserializer::class)
        val amount: BigDecimal
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class OrganizationReferenceSupplierUpdate @JsonCreator constructor(

        var id: String,

        val name: String,

        val identifier: Identifier,

        val address: Address,

        val contactPoint: ContactPoint,

        val additionalIdentifiers: HashSet<Identifier>,

        val persones: HashSet<Person>,

        val details: DetailsSupplierUpdate
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DetailsSupplierUpdate @JsonCreator constructor(

        val typeOfSupplier: String,

        val mainEconomicActivity: Set<String>,

        val scale: String,

        val permits: List<Permits>,

        val bankAccounts: List<BankAccount>,

        val legalForm: LegalForm
)