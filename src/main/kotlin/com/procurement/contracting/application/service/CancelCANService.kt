package com.procurement.contracting.application.service

import com.procurement.contracting.application.repository.ACRepository
import com.procurement.contracting.application.repository.CANRepository
import com.procurement.contracting.application.repository.DataCancelCAN
import com.procurement.contracting.application.repository.DataRelatedCAN
import com.procurement.contracting.domain.entity.ACEntity
import com.procurement.contracting.domain.entity.CANEntity
import com.procurement.contracting.domain.model.can.CAN
import com.procurement.contracting.domain.model.can.CANId
import com.procurement.contracting.domain.model.can.status.CANStatus
import com.procurement.contracting.domain.model.can.status.CANStatusDetails
import com.procurement.contracting.domain.model.contract.status.ContractStatus
import com.procurement.contracting.domain.model.contract.status.ContractStatusDetails
import com.procurement.contracting.domain.model.document.type.DocumentTypeAmendment
import com.procurement.contracting.domain.model.lot.LotId
import com.procurement.contracting.exception.ErrorException
import com.procurement.contracting.exception.ErrorType
import com.procurement.contracting.model.dto.ContractProcess
import com.procurement.contracting.model.dto.ocds.Contract
import com.procurement.contracting.utils.toJson
import com.procurement.contracting.utils.toObject
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

data class CancelCANContext(
    val cpid: String,
    val token: UUID,
    val owner: String,
    val canId: CANId
)

data class CancelCANData(
    val amendment: Amendment
) {

    data class Amendment(
        val rationale: String,
        val description: String?,
        val documents: List<Document>?
    ) {

        data class Document(
            val id: String,
            val documentType: DocumentTypeAmendment,
            val title: String,
            val description: String?
        )
    }
}

data class CancelledCANData(
    val cancelledCAN: CancelledCAN,
    val relatedCANs: List<RelatedCAN>,
    val lotId: LotId,
    val contract: Contract?
) {

    data class CancelledCAN(
        val id: CANId,
        val status: CANStatus,
        val statusDetails: CANStatusDetails,
        val amendment: Amendment
    ) {

        data class Amendment(
            val rationale: String,
            val description: String?,
            val documents: List<Document>?
        ) {

            data class Document(
                val id: String,
                val documentType: DocumentTypeAmendment,
                val title: String,
                val description: String?
            )
        }
    }

    data class RelatedCAN(
        val id: CANId,
        val status: CANStatus,
        val statusDetails: CANStatusDetails
    )

    data class Contract(
        val id: String,
        val status: ContractStatus,
        val statusDetails: ContractStatusDetails
    )
}

interface CancelCANService {
    fun cancel(context: CancelCANContext, data: CancelCANData): CancelledCANData
}

@Service
class CancelCANServiceImpl(
    private val canRepository: CANRepository,
    private val acRepository: ACRepository
) : CancelCANService {
    companion object {
        private val log = LoggerFactory.getLogger(CancelCANService::class.java)
    }
    /**
     * 1. Validates value of token parameter from context of Request by rule VR-9.13.1;
     *
     * 2. Validates value of owner parameter from context of Request by rule VR-9.13.2;
     *
     * 3. Analyzes the availability of Amendment.Documents object in Request:
     *      IF Amendment.Documents object was transferred, eContracting validates Amendments.Documents.documentType
     *         values from Request by rule VR-9.13.5;
     *      ELSE (there is no Documents in Request), eContracting does not perform any operation;
     *
     * 4. Finds saved version of CAN object by ID && CPID values from the context of Request:
     *  a. Validates CAN.Status && CAN.statusDetails in saved version of CAN by rule VR-9.13.3;
     *  b. Checks the availability of ACocid value in saved version of CAN:
     *      i. IF ACocid is filled in saved CAN in DB, eContracting performs next operations:
     *          1. Finds saved version of Contract object by CPID value from the context of Request && ACocid from saved CAN;
     *          2. Validates Contract.Status in saved version of Contract (found before) by rule VR-9.13.4;
     *          3. Sets Contract.status && Contract.statusDetails in saved Contract by rule BR-9.13.2;
     *          4. Saves updated Contract to DB;
     *          5. Returns contract.status && contract.statusDetails && contract.ID for Response;
     *          6. Returns acCancel parameter = TRUE for Response;
     *          7. Checks the availability of other Can objects beside proceeded can related to Contract (found on step 4.b.1) by can.ACocid value:
     *              IF can objects were found, eContracting FOR every can performs next steps:
     *                  i.   Sets CAN.statusDetails by rule BR-9.13.4;
     *                  ii.  Sets CAN.ACocid = NULL;
     *                  iii. Returns CAN from DB for Response as can.ID && can.status && can.statusDetails;
     *              ELSE there is no can related to Contract, eContracting does not perform any actions;
     *          ELSE (ACocid is NOT filled), eContracting returns acCancel parameter = FALSE for Response;
     *  c. Sets CAN.status && CAN.statusDetails by rule BR-9.13.3;
     *  d. Saves Contract.Amendments object as CAN.Amendments objects in DB;
     *  e. Saves updated CAN to DB;
     *  f. Get.can.lotId from saved can (found on step 4) and returns it as lotId value for Response;
     *  g. Returns CAN from DB for Response as can.ID && can.status && can.statusDetails && can.amendments;
     */
    override fun cancel(context: CancelCANContext, data: CancelCANData): CancelledCANData {

        val canEntity: CANEntity = canRepository.findBy(cpid = context.cpid, canId = context.canId)
            ?: throw ErrorException(ErrorType.CAN_NOT_FOUND)

        //VR-9.13.1
        checkToken(tokenFromRequest = context.token, canEntity = canEntity)

        //VR-9.13.2
        checkOwner(ownerFromRequest = context.owner, canEntity = canEntity)

        val can: CAN = toObject(CAN::class.java, canEntity.jsonData)

        //VR-9.13.3
        checkCANStatuses(can)

        val cancelledCAN: CAN = cancellingCAN(can = can, amendment = data.amendment)

        /**
         * Begin processing a contract of the cancellation CAN & related CANs
         */
        val cancelledContract: Contract?
        val updatedContractProcess: ContractProcess?
        val relatedCANs: List<CAN>

        if (canEntity.contractId != null) {
            val contractId: String = canEntity.contractId
            log.debug("CAN with id '${context.canId}' has related AC with id '$contractId'.")
            val acEntity: ACEntity = acRepository.findBy(cpid = context.cpid, contractId = contractId)
                ?: throw ErrorException(ErrorType.CONTRACT_NOT_FOUND)
            log.debug("Founded AC with id '$contractId' for cancelling.")
            val contractProcess: ContractProcess = toObject(ContractProcess::class.java, acEntity.jsonData)

            //VR-9.13.4
            checkContractStatuses(contract = contractProcess.contract)

            cancelledContract = cancellingContract(contractProcess.contract)
            updatedContractProcess = contractProcess.copy(contract = cancelledContract)

            relatedCANs = getRelatedCans(cpid = context.cpid, canId = can.id, contractId = contractId)
                .map { relatedCANEntity ->
                    val relatedCAN: CAN = toObject(CAN::class.java, relatedCANEntity.jsonData)

                    //BR-9.13.4
                    settingStatusesRelatedCAN(relatedCAN)
                }
                .toList()
        } else {
            log.debug("CAN with id '${context.canId}' without AC.")
            cancelledContract = null
            updatedContractProcess = null
            relatedCANs = emptyList()
        }

        if (cancelledContract != null) {
            acRepository.saveCancelledAC(
                cpid = context.cpid,
                id = cancelledContract.id,
                status = cancelledContract.status,
                statusDetails = cancelledContract.statusDetails,
                jsonData = toJson(updatedContractProcess)
            )
        }

        canRepository.saveCancelledCANs(
            cpid = context.cpid,
            dataCancelledCAN = DataCancelCAN(
                id = can.id,
                status = cancelledCAN.status,
                statusDetails = cancelledCAN.statusDetails,
                jsonData = toJson(cancelledCAN)
            ),
            dataRelatedCANs = relatedCANs.map { relatedCan ->
                DataRelatedCAN(
                    id = relatedCan.id,
                    status = relatedCan.status,
                    statusDetails = relatedCan.statusDetails,
                    jsonData = toJson(relatedCan)
                )
            }
        )

        return CancelledCANData(
            cancelledCAN = generateCancelledCANResponse(cancelledCAN),
            relatedCANs = generateRelatedCANsResponse(relatedCANs),
            lotId = can.lotId,
            contract = generateContractResponse(cancelledContract)
        )
    }

    private fun getRelatedCans(
        cpid: String,
        canId: CANId,
        contractId: String
    ): Sequence<CANEntity> = canRepository.findBy(cpid = cpid)
        .asSequence()
        .filter {
            it.contractId == contractId && it.id != canId
        }

    private fun generateCancelledCANResponse(cancelledCAN: CAN) = CancelledCANData.CancelledCAN(
        id = cancelledCAN.id,
        status = cancelledCAN.status,
        statusDetails = cancelledCAN.statusDetails,
        amendment = cancelledCAN.amendment!!.let {
            CancelledCANData.CancelledCAN.Amendment(
                rationale = it.rationale,
                description = it.description,
                documents = it.documents?.map { document ->
                    CancelledCANData.CancelledCAN.Amendment.Document(
                        id = document.id,
                        documentType = document.documentType,
                        title = document.title,
                        description = document.description
                    )
                }
            )
        }
    )

    private fun generateRelatedCANsResponse(relatedCANs: List<CAN>): List<CancelledCANData.RelatedCAN> =
        relatedCANs.map { relatedCAN ->
            CancelledCANData.RelatedCAN(
                id = relatedCAN.id,
                status = relatedCAN.status,
                statusDetails = relatedCAN.statusDetails
            )
        }

    private fun generateContractResponse(contract: Contract?) = contract?.let {
        CancelledCANData.Contract(
            id = it.id,
            status = it.status,
            statusDetails = it.statusDetails
        )
    }

    private fun cancellingContract(contract: Contract): Contract {
        return contract.copy(
            /**
             * BR-9.13.2 Contract.statusDetails Contract.status (contract)
             *
             * eContracting sets Contract.status == "cancelled" && Contract.statusDetails value == "empty" and saves them to DB;
             */
            status = ContractStatus.CANCELLED,
            statusDetails = ContractStatusDetails.EMPTY
        )
    }

    private fun cancellingCAN(can: CAN, amendment: CancelCANData.Amendment): CAN {
        return can.copy(
            /**
             * BR-9.13.3 Status statusDetails (CAN)
             *
             * eContracting sets CAN.status == "cancelled" && CAN.statusDetails value == "empty" and saves them to DB;
             */
            status = CANStatus.CANCELLED,
            statusDetails = CANStatusDetails.EMPTY,

            /**
             * BR-9.13.1(4.d)
             */
            amendment = CAN.Amendment(
                rationale = amendment.rationale,
                description = amendment.description,
                documents = amendment.documents?.map { document ->
                    CAN.Amendment.Document(
                        id = document.id,
                        documentType = document.documentType,
                        title = document.title,
                        description = document.description
                    )
                }
            )
        )
    }

    /**
     * BR-9.13.4 statusDetails (CAN)
     *
     * eContracting sets CAN.statusDetails value == "contractProject" and saves it to DB;
     */
    private fun settingStatusesRelatedCAN(can: CAN): CAN {
        return can.copy(
            statusDetails = CANStatusDetails.CONTRACT_PROJECT
        )
    }

    /**
     * VR-9.13.1 ID (can) token
     *
     * eContracting проверяет что найденный по ID && CPID из запароса CAN содержит token,
     * значение которого равно значению параметра token из запроса.
     */
    private fun checkToken(tokenFromRequest: UUID, canEntity: CANEntity) {
        if (canEntity.token != tokenFromRequest)
            throw ErrorException(error = ErrorType.INVALID_TOKEN)
    }

    /**
     * VR-9.13.2 Owner
     *
     * eContracting проверяет соответствие owner связанного CAN (выбранного из БД) и owner,
     * полученного в контексте запроса.
     */
    private fun checkOwner(ownerFromRequest: String, canEntity: CANEntity) {
        if (canEntity.owner != ownerFromRequest)
            throw ErrorException(error = ErrorType.INVALID_OWNER)
    }

    /**
     * VR-9.13.3 Status statusDetails (CAN)
     *
     * eContracting checks can.status && can.statusDetails in saved version of CAN:
     * IF can.status value == "pending" && can.statusDetails value == "contractProject" || "active" || "unsuccessful",
     *      validation is successful;
     * ELSE eContracting throws Exception;
     */
    private fun checkCANStatuses(can: CAN) {
        when (can.status) {
            CANStatus.PENDING -> {
                return when (can.statusDetails) {
                    CANStatusDetails.CONTRACT_PROJECT,
                    CANStatusDetails.ACTIVE,
                    CANStatusDetails.UNSUCCESSFUL -> Unit

                    CANStatusDetails.EMPTY,
                    CANStatusDetails.TREASURY_REJECTION -> throw ErrorException(error = ErrorType.INVALID_CAN_STATUS_DETAILS)
                }
            }

            CANStatus.ACTIVE,
            CANStatus.CANCELLED,
            CANStatus.UNSUCCESSFUL -> throw ErrorException(error = ErrorType.INVALID_CAN_STATUS)
        }
    }

    /**
     * VR-9.13.4 Contract.status (contract)
     *
     * eContracting checks Contract.Status in saved version of Contract:
     * IF (contract.status value == "pending") && (contract.statusDetails value != "verification" || "verified"),
     *    validation is successful;
     * ELSE eContracting throws Exception;
     */
    private fun checkContractStatuses(contract: Contract) {
        when (contract.status) {
            ContractStatus.PENDING -> {
                return when (contract.statusDetails) {
                    ContractStatusDetails.CONTRACT_PROJECT,
                    ContractStatusDetails.CONTRACT_PREPARATION,
                    ContractStatusDetails.APPROVED,
                    ContractStatusDetails.SIGNED,

                    ContractStatusDetails.ISSUED,
                    ContractStatusDetails.APPROVEMENT,
                    ContractStatusDetails.EXECUTION,
                    ContractStatusDetails.EMPTY -> Unit

                    ContractStatusDetails.VERIFICATION,
                    ContractStatusDetails.VERIFIED -> throw ErrorException(error = ErrorType.CONTRACT_STATUS_DETAILS)
                }
            }

            ContractStatus.CANCELLED -> {
                return when (contract.statusDetails) {
                    ContractStatusDetails.EMPTY -> Unit

                    ContractStatusDetails.CONTRACT_PROJECT,
                    ContractStatusDetails.CONTRACT_PREPARATION,
                    ContractStatusDetails.APPROVED,
                    ContractStatusDetails.SIGNED,
                    ContractStatusDetails.VERIFICATION,
                    ContractStatusDetails.VERIFIED,
                    ContractStatusDetails.ISSUED,
                    ContractStatusDetails.APPROVEMENT,
                    ContractStatusDetails.EXECUTION -> throw ErrorException(error = ErrorType.CONTRACT_STATUS_DETAILS)
                }
            }

            ContractStatus.ACTIVE,
            ContractStatus.COMPLETE,
            ContractStatus.TERMINATED,
            ContractStatus.UNSUCCESSFUL -> throw ErrorException(error = ErrorType.CONTRACT_STATUS)
        }
    }
}
