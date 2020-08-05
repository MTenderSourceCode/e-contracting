package com.procurement.contracting.application.service.ac

import com.procurement.contracting.application.repository.ACRepository
import com.procurement.contracting.application.repository.CANRepository
import com.procurement.contracting.application.repository.RelatedContract
import com.procurement.contracting.domain.entity.ACEntity
import com.procurement.contracting.domain.entity.CANEntity
import com.procurement.contracting.domain.model.ProcurementMethod
import com.procurement.contracting.domain.model.award.AwardId
import com.procurement.contracting.domain.model.bid.BidId
import com.procurement.contracting.domain.model.can.CAN
import com.procurement.contracting.domain.model.can.status.CANStatus
import com.procurement.contracting.domain.model.can.status.CANStatusDetails
import com.procurement.contracting.domain.model.contract.status.ContractStatus
import com.procurement.contracting.domain.model.contract.status.ContractStatusDetails
import com.procurement.contracting.domain.model.lot.LotId
import com.procurement.contracting.exception.ErrorException
import com.procurement.contracting.exception.ErrorType
import com.procurement.contracting.model.dto.ContractProcess
import com.procurement.contracting.model.dto.ocds.Address
import com.procurement.contracting.model.dto.ocds.AddressDetails
import com.procurement.contracting.model.dto.ocds.Classification
import com.procurement.contracting.model.dto.ocds.ContactPoint
import com.procurement.contracting.model.dto.ocds.Contract
import com.procurement.contracting.model.dto.ocds.ContractedAward
import com.procurement.contracting.model.dto.ocds.CountryDetails
import com.procurement.contracting.model.dto.ocds.DocumentAward
import com.procurement.contracting.model.dto.ocds.Identifier
import com.procurement.contracting.model.dto.ocds.Item
import com.procurement.contracting.model.dto.ocds.LocalityDetails
import com.procurement.contracting.model.dto.ocds.OrganizationReferenceSupplier
import com.procurement.contracting.model.dto.ocds.RegionDetails
import com.procurement.contracting.model.dto.ocds.ValueTax
import com.procurement.contracting.service.GenerationService
import com.procurement.contracting.utils.toJson
import com.procurement.contracting.utils.toObject
import org.springframework.stereotype.Service
import java.math.RoundingMode
import java.util.*

interface ACService {
    fun create(context: CreateACContext, data: CreateACData): CreatedACData
}

@Service
class ACServiceImpl(
    private val canRepository: CANRepository,
    private val acRepository: ACRepository,
    private val generationService: GenerationService
) : ACService {

    /**
     * eContracting executes next operations:
     * 1. Finds every saved CAN objects by CPID && ID (contract.id) values from Request
     *    and saves them as a list to memory (IF at least one can was not found: Exception "Invalid Can IDs");
     * 2. Validates all can objects from list got before by rule VR-9.1.3;
     * 3. Sets can.statusDetails in every can object from list (got on step 1) by rule BR-9.1.3;
     * 4. Saves all updated Cans to DB;
     * 5. Generates contractedAward object by rule BR-9.1.12;
     * 6. Generates contract object by rule BR-9.1.13;
     * 7. Sets can.ocidAc values in all cans from list (got on step 1) == contract.ID generated by BR-9.1.13
     *    and saves them to DB;
     * 8. Saves created contractedAward && contract object to DB with next parameters:
     *   a. contractedTender.mainProcurementCategory value from Request;
     *   b. Owner value from the context of Request;
     *   c. CPID value from the context of Request;
     *   d. Language value from the context of Request;
     *   e. Token value generated by BR-9.1.13;
     *   f. ocidAC == contract.ID generated by BR-9.1.13;
     *   g. relatedAwards value generated by BR-9.1.12;
     *   h. relatedBids value generated by BR-9.1.12;
     * 9. Returns for Response next objects:
     *   a. contract object;
     *   b. contractedAward object:
     *     - contractedAward.ID;
     *     - contractedAward.date;
     *     - contractedAward.value.amount;
     *     - contractedAward.value.currency;
     *     - contractedAward.relatedLots;
     *     - contractedAward.suppliers;
     *     - contractedAward.items;
     *     - contractedAward.documents;
     *   c. Array updated can objects (selected on step 1):
     *     - can.id;
     *     - can.status;
     *     - can.statusDetails;
     *   d. token;
     */
    override fun create(context: CreateACContext, data: CreateACData): CreatedACData {
        //VR-9.1.1
        checkSuppliersId(data = data)

        //VR-9.1.2
        checkAwardCurrency(data = data)

        val entities: List<CANEntity> = loadCANs(cpid = context.cpid, cans = data.cans)

        //VR-9.1.3
        checkStatuses(entities)

        //BR-9.1.12
        val contractedAward = generateContractedAward(context = context, data = data)

        //BR-9.1.13
        val contract: Contract = generateContract(cpid = context.cpid, contractedAward = contractedAward)

        val contractProcess = ContractProcess(
            contract = contract,
            award = contractedAward
        )

        val updatedCANS: Map<CANEntity, CAN> = updateCans(entities = entities, contract = contract)

        val updatedCANsEntities = updatedCANS.keys.map { entity ->
            RelatedContract(
                id = entity.id,
                contractId = entity.contractId!!,
                status = entity.status,
                statusDetails = entity.statusDetails,
                jsonData = entity.jsonData
            )
        }

        val acEntity = ACEntity(
            cpid = context.cpid,
            id = contract.id,
            token = UUID.fromString(contract.token),
            owner = context.owner,
            createdDate = context.startDate,
            status = contract.status,
            statusDetails = contract.statusDetails,
            mainProcurementCategory = data.contractedTender.mainProcurementCategory,
            language = context.language,
            jsonData = toJson(contractProcess)
        )

        canRepository.relateContract(cpid = context.cpid, cans = updatedCANsEntities)
        acRepository.saveNew(acEntity)

        return CreatedACData(
            token = UUID.fromString(contract.token),
            cans = updatedCANS.values.map { can ->
                CreatedACData.CAN(
                    id = can.id,
                    status = can.status,
                    statusDetails = can.statusDetails
                )
            },
            contract = CreatedACData.Contract(
                id = contract.id,
                awardId = contract.awardId,
                status = contract.status,
                statusDetails = contract.statusDetails
            ),
            contractedAward = CreatedACData.ContractedAward(
                id = contractedAward.id,
                date = contractedAward.date,
                value = contractedAward.value.let { value ->
                    CreatedACData.ContractedAward.Value(
                        amount = value.amount!!,
                        currency = value.currency!!
                    )
                },
                relatedLots = contractedAward.relatedLots.toList(),
                suppliers = contractedAward.suppliers.map { supplier ->
                    CreatedACData.ContractedAward.Supplier(
                        id = supplier.id,
                        name = supplier.name,
                        identifier = supplier.identifier.let { identifier ->
                            CreatedACData.ContractedAward.Supplier.Identifier(
                                scheme = identifier.scheme,
                                id = identifier.id,
                                legalName = identifier.legalName!!,
                                uri = identifier.uri
                            )
                        },
                        additionalIdentifiers = supplier.additionalIdentifiers?.map { additionalIdentifier ->
                            CreatedACData.ContractedAward.Supplier.AdditionalIdentifier(
                                scheme = additionalIdentifier.scheme,
                                id = additionalIdentifier.id,
                                legalName = additionalIdentifier.legalName!!,
                                uri = additionalIdentifier.uri
                            )
                        },
                        address = supplier.address.let { address ->
                            CreatedACData.ContractedAward.Supplier.Address(
                                streetAddress = address.streetAddress,
                                postalCode = address.postalCode,
                                addressDetails = address.addressDetails.let { addressDetails ->
                                    CreatedACData.ContractedAward.Supplier.Address.AddressDetails(
                                        country = addressDetails.country.let { country ->
                                            CreatedACData.ContractedAward.Supplier.Address.AddressDetails.Country(
                                                scheme = country.scheme,
                                                id = country.id,
                                                description = country.description,
                                                uri = country.uri
                                            )
                                        },
                                        region = addressDetails.region.let { region ->
                                            CreatedACData.ContractedAward.Supplier.Address.AddressDetails.Region(
                                                scheme = region.scheme,
                                                id = region.id,
                                                description = region.description,
                                                uri = region.uri
                                            )
                                        },
                                        locality = addressDetails.locality.let { locality ->
                                            CreatedACData.ContractedAward.Supplier.Address.AddressDetails.Locality(
                                                scheme = locality.scheme,
                                                id = locality.id,
                                                description = locality.description,
                                                uri = locality.uri
                                            )
                                        }
                                    )
                                }
                            )
                        },
                        contactPoint = supplier.contactPoint.let { contactPoint ->
                            CreatedACData.ContractedAward.Supplier.ContactPoint(
                                name = contactPoint.name,
                                email = contactPoint.email,
                                telephone = contactPoint.telephone,
                                faxNumber = contactPoint.faxNumber,
                                url = contactPoint.url
                            )
                        }
                    )
                },
                documents = contractedAward.documents!!.map { document ->
                    CreatedACData.ContractedAward.Document(
                        documentType = document.documentType,
                        id = document.id,
                        title = document.title,
                        description = document.description,
                        relatedLots = document.relatedLots?.toList()
                    )
                },
                items = contractedAward.items.map { item ->
                    CreatedACData.ContractedAward.Item(
                        id = item.id,
                        internalId = item.internalId,
                        classification = item.classification.let { classification ->
                            CreatedACData.ContractedAward.Item.Classification(
                                scheme = classification.scheme,
                                id = classification.id,
                                description = classification.description
                            )
                        },
                        additionalClassifications = item.additionalClassifications?.map { additionalClassification ->
                            CreatedACData.ContractedAward.Item.AdditionalClassification(
                                scheme = additionalClassification.scheme,
                                id = additionalClassification.id,
                                description = additionalClassification.description
                            )
                        },
                        quantity = item.quantity,
                        unit = item.unit.let { unit ->
                            CreatedACData.ContractedAward.Item.Unit(
                                id = unit.id,
                                name = unit.name
                            )
                        },
                        description = item.description!!,
                        relatedLot = item.relatedLot
                    )
                }
            )
        )
    }

    private fun loadCANs(cpid: String, cans: List<CreateACData.CAN>): List<CANEntity> {
        val cansIds = cans.asSequence()
            .map { can -> can.id }
            .toSet()
        return canRepository.findBy(cpid = cpid)
            .filter { entity ->
                cansIds.contains(entity.id)
            }
    }

    /**
     * VR-9.1.1 Supplier.ID (Award)
     *
     * eContracting checks Award.Supplier.ID in all award objects from Request:
     * a. IF award.supplier.ID is the same for every Award object from Request (one value in formed set of Supplier.ID)
     *    validation is successful;
     * b. ELSE (more than one value in set of Supplier.ID)
     *    eContracting throws Exception;
     */
    private fun checkSuppliersId(data: CreateACData) {
        val uniqueSuppliersIds = data.awards.asSequence()
            .flatMap { award ->
                award.suppliers
                    .asSequence()
                    .map { supplier ->
                        supplier.id
                    }
            }
            .toSet()
        if (uniqueSuppliersIds.size != 1) throw ErrorException(error = ErrorType.SUPPLIERS_ID)
    }

    /**
     * VR-9.1.2 Value.Currency (Award)
     *
     * eContracting checks Award.Value.Currency in all award objects from Request:
     * a. IF award.value.currency is the same for every Award object from Request (one value in formed set of Currency)
     *    validation is successful;
     * b. ELSE (more than one value in set of Currency)
     *    eContracting throws Exception;
     */
    private fun checkAwardCurrency(data: CreateACData) {
        val currencies: Set<String> = data.awards.asSequence()
            .map { award ->
                award.value.currency
            }
            .toSet()

        if (currencies.size != 1) throw ErrorException(error = ErrorType.AWARD_CURRENCY)
    }

    /**
     *  VR-9.1.3 "status" "statusDetails" (can)
     *
     * eContracting executes next operations:
     * 1. FOR every proceeded can object eContracting checks can.status && can.statusDetails:
     *   a. IF can.status == "pending" && can.statusDetails == "contractProject"
     *      validation is successful;
     *   b. ELSE (can.status != "pending" && can.statusDetails != "contractProject")
     *      thrown Exception: "CAN has been used for AC generation";
     */
    private fun checkStatuses(entities: List<CANEntity>) {
        entities.forEach { entity ->
            when (entity.status) {
                CANStatus.PENDING -> {
                    when (entity.statusDetails) {
                        CANStatusDetails.CONTRACT_PROJECT -> Unit
                        CANStatusDetails.ACTIVE,
                        CANStatusDetails.UNSUCCESSFUL,
                        CANStatusDetails.EMPTY,
                        CANStatusDetails.TREASURY_REJECTION -> throw ErrorException(error = ErrorType.CAN_ALREADY_USED)
                    }
                }
                CANStatus.ACTIVE,
                CANStatus.CANCELLED,
                CANStatus.UNSUCCESSFUL -> throw ErrorException(error = ErrorType.CAN_ALREADY_USED)
            }
        }
    }

    private fun updateCans(entities: List<CANEntity>, contract: Contract): Map<CANEntity, CAN> {
        return mutableMapOf<CANEntity, CAN>().apply {
            val contractId = contract.id
            for (entity in entities) {
                val can = toObject(CAN::class.java, entity.jsonData)
                val updatedCAN = can.copy(statusDetails = CANStatusDetails.ACTIVE)

                val updatedEntity = entity.copy(
                    contractId = contractId,
                    statusDetails = updatedCAN.statusDetails,
                    jsonData = toJson(updatedCAN)
                )
                put(updatedEntity, updatedCAN)
            }
        }
    }

    /**
     * BR-9.1.13 contract
     */
    private fun generateContract(cpid: String, contractedAward: ContractedAward): Contract {
        return Contract(
            id = generationService.contractId(cpid),
            token = generationService.token().toString(),
            awardId = contractedAward.id,
            //BR-9.1.2
            status = ContractStatus.PENDING,

            //BR-9.1.2
            statusDetails = ContractStatusDetails.CONTRACT_PROJECT
        )
    }

    /**
     * BR-9.1.12 contractedAward
     */
    private fun generateContractedAward(context: CreateACContext, data: CreateACData): ContractedAward =
        ContractedAward(
            id = generationService.awardId(),
            date = context.startDate,
            relatedLots = generateRelatedLots(awards = data.awards),
            relatedBids = generateRelatedBids(context = context, awards = data.awards),
            relatedAwards = generateRelatedAwards(awards = data.awards),
            value = generateValueTax(awards = data.awards),
            items = generateItems(items = data.contractedTender.items),
            documents = generateDocuments(awards = data.awards),
            suppliers = generateSuppliers(awards = data.awards)
        )

    private fun generateValueTax(awards: List<CreateACData.Award>): ValueTax {
        val amountSum = awards.asSequence()
            .sumByDouble { it.value.amount.toDouble() }
            .toBigDecimal().setScale(2, RoundingMode.HALF_UP)
        val amountCurrency = awards.first().value.currency
        return ValueTax(
            amount = amountSum,
            currency = amountCurrency
        )
    }

    private fun generateRelatedLots(awards: List<CreateACData.Award>): List<LotId> = awards.asSequence()
        .flatMap { award ->
            award.relatedLots.asSequence()
        }
        .distinct()
        .toList()

    private fun generateRelatedAwards(awards: List<CreateACData.Award>): List<AwardId> = awards.map { award ->
        award.id
    }

    private fun generateRelatedBids(context: CreateACContext, awards: List<CreateACData.Award>): List<BidId> =
        when (context.pmd) {
            ProcurementMethod.OT,
            ProcurementMethod.TEST_OT,
            ProcurementMethod.SV,
            ProcurementMethod.TEST_SV,
            ProcurementMethod.MV,
            ProcurementMethod.TEST_MV,
            ProcurementMethod.GPA,
            ProcurementMethod.TEST_GPA -> {
                awards.map { award ->
                    award.relatedBid!!
                }
            }
            ProcurementMethod.RT,
            ProcurementMethod.TEST_RT,
            ProcurementMethod.FA,
            ProcurementMethod.TEST_FA,
            ProcurementMethod.OP,
            ProcurementMethod.TEST_OP,
            ProcurementMethod.DA,
            ProcurementMethod.TEST_DA,
            ProcurementMethod.NP,
            ProcurementMethod.TEST_NP -> {
                emptyList()
            }
        }

    private fun generateItems(items: List<CreateACData.ContractedTender.Item>): List<Item> = items.asSequence()
        .map { item ->
            Item(
                id = item.id,
                internalId = item.internalId,
                classification = item.classification.let { classification ->
                    Classification(
                        scheme = classification.scheme,
                        id = classification.id,
                        description = classification.description,
                        uri = null
                    )
                },
                additionalClassifications = item.additionalClassifications
                    ?.asSequence()
                    ?.map { additionalClassification ->
                        Classification(
                            scheme = additionalClassification.scheme,
                            id = additionalClassification.id,
                            description = additionalClassification.description,
                            uri = null
                        )
                    }
                    ?.toSet(),
                quantity = item.quantity,
                unit = item.unit.let { unit ->
                    com.procurement.contracting.model.dto.ocds.Unit(
                        id = unit.id,
                        name = unit.name,
                        value = null
                    )
                },
                description = item.description,
                relatedLot = item.relatedLot,
                deliveryAddress = null
            )
        }
        .toList()

    private fun generateDocuments(awards: List<CreateACData.Award>): List<DocumentAward> = awards.asSequence()
        .flatMap { award ->
            award.documents?.asSequence() ?: emptySequence()
        }
        .distinctBy { document ->
            document.id
        }
        .map { document ->
            DocumentAward(
                id = document.id,
                documentType = document.documentType,
                title = document.title,
                description = document.description,
                relatedLots = document.relatedLots?.toList()
            )
        }
        .toList()

    private fun generateSuppliers(awards: List<CreateACData.Award>): List<OrganizationReferenceSupplier> =
        awards.asSequence()
            .flatMap { award ->
                award.suppliers.asSequence()
            }
            .distinctBy { supplier ->
                supplier.id
            }
            .map { supplier ->
                OrganizationReferenceSupplier(
                    id = supplier.id,
                    name = supplier.name,
                    identifier = supplier.identifier.let { identifier ->
                        Identifier(
                            scheme = identifier.scheme,
                            id = identifier.id,
                            legalName = identifier.legalName,
                            uri = identifier.uri
                        )
                    },
                    additionalIdentifiers = supplier.additionalIdentifiers
                        ?.asSequence()
                        ?.map { additionalIdentifier ->
                            Identifier(
                                scheme = additionalIdentifier.scheme,
                                id = additionalIdentifier.id,
                                legalName = additionalIdentifier.legalName,
                                uri = additionalIdentifier.uri
                            )
                        }
                        ?.toHashSet(),
                    address = supplier.address.let { address ->
                        Address(
                            streetAddress = address.streetAddress,
                            postalCode = address.postalCode,
                            addressDetails = address.addressDetails.let { addressDetails ->
                                AddressDetails(
                                    country = addressDetails.country.let { country ->
                                        CountryDetails(
                                            scheme = country.scheme,
                                            id = country.id,
                                            description = country.description,
                                            uri = country.uri
                                        )
                                    },
                                    region = addressDetails.region.let { region ->
                                        RegionDetails(
                                            scheme = region.scheme,
                                            id = region.id,
                                            description = region.description,
                                            uri = region.uri
                                        )
                                    },
                                    locality = addressDetails.locality.let { locality ->
                                        LocalityDetails(
                                            scheme = locality.scheme,
                                            id = locality.id,
                                            description = locality.description,
                                            uri = locality.uri
                                        )
                                    }
                                )
                            }
                        )
                    },
                    contactPoint = supplier.contactPoint.let { contactPoint ->
                        ContactPoint(
                            name = contactPoint.name,
                            email = contactPoint.email,
                            telephone = contactPoint.telephone,
                            faxNumber = contactPoint.faxNumber,
                            url = contactPoint.url
                        )
                    },
                    details = null,
                    persones = null
                )
            }
            .toList()
}
