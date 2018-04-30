package com.procurement.contracting.service;

import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.uuid.Generators;
import com.procurement.contracting.exception.ErrorException;
import com.procurement.contracting.exception.ErrorType;
import com.procurement.contracting.model.dto.ContractDocumentDto;
import com.procurement.contracting.model.dto.ContractStatus;
import com.procurement.contracting.model.dto.ContractStatusDetails;
import com.procurement.contracting.model.dto.awardedContract.ACContractDto;
import com.procurement.contracting.model.dto.awardedContract.ACDto;
import com.procurement.contracting.model.dto.awardedContract.CreateACContractingLotRQDto;
import com.procurement.contracting.model.dto.awardedContract.CreateACRQ;
import com.procurement.contracting.model.dto.bpe.ResponseDto;
import com.procurement.contracting.model.dto.changeStatus.ChangeStatusRQ;
import com.procurement.contracting.model.dto.checkContract.CheckAcRS;
import com.procurement.contracting.model.dto.updateAC.UpdateACRQ;
import com.procurement.contracting.model.entity.ACEntity;
import com.procurement.contracting.model.entity.CANEntity;
import com.procurement.contracting.repository.ACRepository;
import com.procurement.contracting.repository.CANRepository;
import com.procurement.contracting.utils.DateUtil;
import com.procurement.contracting.utils.JsonUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ACServiceImpl implements ACService {
    private final ACRepository acRepository;
    private final CANRepository canRepository;
    private final DateUtil dateUtil;
    private final JsonUtil jsonUtil;

    public ACServiceImpl(final ACRepository acRepository,
                         final CANRepository canRepository,
                         final DateUtil dateUtil, final JsonUtil jsonUtil) {
        this.acRepository = acRepository;
        this.canRepository = canRepository;
        this.dateUtil = dateUtil;
        this.jsonUtil = jsonUtil;
    }

    @Override
    public ResponseDto createAC(final String cpId, final String token, final CreateACRQ createACRQ) {
        final CANEntity canEntity = Optional.ofNullable(canRepository.getByCpIdAndToken(cpId, UUID.fromString(token)))
                .orElseThrow(() -> new ErrorException(ErrorType.CAN_NOT_FOUND));
        if (canEntity.getAcId() == null)
            throw new ErrorException(ErrorType.INVALID_ID);

        if (createACRQ.getContract().getId().equals(canEntity.getAcId().toString()))
            throw new ErrorException(ErrorType.ALREADY_CREATED);

        canEntity.setStatus(ContractStatus.ACTIVE.toString());
        canEntity.setStatusDetails(null);

        final ACDto acDto = createCreateACRSFromRQ(createACRQ, token);

        final ACEntity acEntity = convertACDtoToACEntity(cpId, acDto);
        final String owner = canRepository.getOwnerByCpIdAndToken(cpId, UUID.fromString(acDto.getContracts().getExtendsContractId()));
        acEntity.setOwner(owner);
        acEntity.setCreatedDate(dateUtil.localToDate(dateUtil.getNowUTC()));
        canEntity.setAcId(acEntity.getToken());
        canRepository.save(canEntity);
        acRepository.save(acEntity);


        responseDto.setSuccess(true);
        responseDto.setData(acDto);
        final ResponseDto responseDto = new ResponseDto(null, null, null);
        return responseDto;
    }

    private ACDto createCreateACRSFromRQ(final CreateACRQ createACRQ,
                                         final String canId) {

        final List<ACDto> createACRSList = new ArrayList<>();
        for (CreateACContractingLotRQDto lot :createACRQ.getLots()) {

            final String contractTitle = lot.getTitle();

            final String contractDescription = lot.getDescription();

            final UUID acId = UUIDs.random();

            final ACContractDto createACRSDto = new ACContractDto(
                    acId.toString(),
                    createACRQ.getContract().getAwardID(),
                    canId,
                    null,
                    contractTitle,
                    contractDescription,
                    ContractStatus.PENDING,
                    ContractStatusDetails.CONTRACT_PROJECT,
                    null,
                    null,
                    createACRQ.getAward().getValue(),
                    createACRQ.getItems(),
                    null,
                    null,
                    null,
                    null);

            createACRSList.add(new ACDto(acId.toString(), contractStatus, statusDetails, createACRSDto));
        }
        return createACRSList.get(0);
    }


    @Override
    public ResponseDto updateAC(final String cpId,
                                final String token,
                                final String platformId,
                                final UpdateACRQ updateACRQ) {

        final ACEntity acEntity = acRepository.getByCpIdAndAcId(cpId, UUID.fromString(token));
        final ResponseDto responseDto = new ResponseDto(null, null, null);
        if (acEntity != null) {
            if (acEntity.getOwner()
                    .equals(platformId)) {
                if (updateACRQ.getContracts()
                        .getAmendments()
                        .size() > 0) {
                    final String jsonData = acEntity.getJsonData();
                    final ACDto acDto = jsonUtil.toObject(ACDto.class, jsonData);
                    //title description
                    if (isTitleOrDescriptionChanged(acDto, updateACRQ)
                            && isStatusPending(acEntity)) {
                        acDto.getContracts()
                                .setTitle(updateACRQ.getContracts()
                                        .getTitle());
                        acDto.getContracts()
                                .setDescription(updateACRQ.getContracts()
                                        .getDescription());
                    }
                    //documents
                    if (updateACRQ.getContracts()
                            .getDocuments() != null) {
                        final List<ContractDocumentDto> savedDocs = acDto.getContracts()
                                .getDocuments();
                        final List<ContractDocumentDto> newDocs = getNewDocumets(savedDocs, updateACRQ.getContracts()
                                .getDocuments());
                        if (newDocs.size() > 0) {

                            if (isStatusPending(acEntity)) {
                                if (isValidDocumentsFromPending(newDocs)) {
                                    savedDocs.addAll(newDocs);
                                    acDto.getContracts()
                                            .setDocuments(savedDocs);
                                } else {
                                    throw new ErrorException(ErrorType.DOCUMENTS_IS_INVALID);
                                }
                            } else if (isStatusActive(acEntity)) {
                                if (isValidDocumentFromActive(newDocs)) {
                                    savedDocs.addAll(newDocs);
                                    acDto.getContracts()
                                            .setDocuments(newDocs);
                                } else {
                                    throw new ErrorException(ErrorType.DOCUMENTS_IS_INVALID);
                                }
                            }
                        }
                    }

                    //date

                    final LocalDateTime contractStartDate = updateACRQ.getContracts()
                            .getPeriod()
                            .getStartDate();
                    if ((isStatusActive(acEntity) || isStatusPending(acEntity))
                            && contractStartDate.isAfter(dateUtil.getNowUTC())) {
                        acDto.getContracts()
                                .getPeriod()
                                .setStartDate(contractStartDate);
                    }

                    final LocalDateTime contractEndDate = updateACRQ.getContracts()
                            .getPeriod()
                            .getEndDate();
                    if ((isStatusActive(acEntity) || isStatusPending(acEntity))
                            && contractEndDate.isAfter(contractStartDate)) {
                        acDto.getContracts()
                                .getPeriod()
                                .setEndDate(contractEndDate);
                    }
                    final LocalDateTime contractDateSigned = updateACRQ.getContracts()
                            .getDateSigned();

                    if (contractDateSigned != null) {
                        if (isStatusPending(acEntity) && contractDateSigned.isBefore(dateUtil.getNowUTC())) {
                            acDto.getContracts()
                                    .setDateSigned(contractDateSigned);
                        }
                    }

                    //budget
                    final double contractBudget = acDto.getContracts()
                            .getValue()
                            .getAmount();
                    final double sumBudgetSources = sumBudgetSourceFromRequestDto(updateACRQ);
                    if (contractBudget == sumBudgetSources) {
                        acDto.getContracts()
                                .setBudgetSource(updateACRQ.getContracts()
                                        .getBudgetSource());
                    } else {
                        throw new ErrorException(ErrorType.BUDGET_SUM_IS_NOT_VALID);
                    }

                    acEntity.setCreatedDate(dateUtil.localToDate(dateUtil.getNowUTC()));
                    acEntity.setJsonData(jsonUtil.toJson(acDto));
                    acRepository.save(acEntity);
                } else {
                    throw new ErrorException(ErrorType.NO_AMENDMENTS);
                }
            } else {
                throw new ErrorException(ErrorType.INVALID_PLATFORM);
            }

            responseDto.setSuccess(true);
            responseDto.setData(responseDto);
        } else {
            throw new ErrorException(ErrorType.AC_NOT_FOUND);
        }

        return responseDto;
    }

    @Override
    public ResponseDto changeStatus(final String cpId,
                                    final String token,
                                    final String platformId,
                                    final ChangeStatusRQ changeStatusRQ) {

        final ACEntity acEntity = acRepository.getByCpIdAndAcId(cpId, UUID.fromString(token));

        final ResponseDto responseDto = new ResponseDto(null, null, null);
        if (acEntity != null) {
            final ACDto acDto = jsonUtil.toObject(ACDto.class, acEntity.getJsonData());
            if (acEntity.getOwner()
                    .equals(platformId)) {
                if (changeStatusRQ.getContracts()
                        .getAmendments()
                        .size() > 0) {
                    final List<ContractDocumentDto> docs = changeStatusRQ.getContracts()
                            .getDocuments();
                    switch (changeStatusRQ.getContracts()
                            .getStatusDetails()) {
                        case ACTIVE:
                            if (acDto.getContracts()
                                    .getDateSigned() != null) {
                                acDto.getContracts()
                                        .setStatusDetails(ContractStatusDetails.ACTIVE);
                                acDto.getContracts()
                                        .setStatus(ContractStatus.ACTIVE);
                                acEntity.setStatus(ContractStatus.ACTIVE.toString());
                                acEntity.setStatusDetails(ContractStatusDetails.ACTIVE.toString());
                            } else {
                                throw new ErrorException(ErrorType.INVALID_DATE);
                            }
                            break;
                        case CANCELLED:

                            if (isDocumentTypePresent(docs, ContractDocumentDto.DocumentType.CANCELLATION_DETAILS)
                                    &&
                                    isValidDocumentsFromStatusCancel(docs)) {
                                acDto.getContracts()
                                        .setStatusDetails(ContractStatusDetails.CANCELLED);
                                acDto.getContracts()
                                        .setStatus(ContractStatus.CANCELLED);
                                acEntity.setStatus(ContractStatus.CANCELLED.toString());
                                acEntity.setStatusDetails(ContractStatusDetails.CANCELLED.toString());
                            } else {
                                throw new ErrorException(ErrorType.DOCUMENTS_IS_INVALID);
                            }
                            break;
                        case COMPLETE:
                            if (isValidDocumentsFromStatusComplete(changeStatusRQ.getContracts()
                                    .getDocuments())) {
                                acDto.getContracts()
                                        .setStatusDetails(ContractStatusDetails.COMPLETE);
                                acDto.getContracts()
                                        .setStatus(ContractStatus.COMPLETE);
                                acEntity.setStatusDetails(ContractStatus.COMPLETE.toString());
                                acEntity.setStatus(ContractStatus.COMPLETE.toString());
                            } else {
                                throw new ErrorException(ErrorType.DOCUMENTS_IS_INVALID);
                            }
                            break;
                        case UNSUCCESSFUL:
                            if (isDocumentTypePresent(docs, ContractDocumentDto.DocumentType.CANCELLATION_DETAILS)
                                    && isValidDocumentsFromStatusUnsuccessful(docs)) {
                                acDto.getContracts()
                                        .setStatusDetails(ContractStatusDetails.UNSUCCESSFUL);
                                acDto.getContracts()
                                        .setStatus(ContractStatus.TERMINATED);
                                acEntity.setStatusDetails(ContractStatusDetails.UNSUCCESSFUL.toString());
                                acEntity.setStatus(ContractStatus.TERMINATED.toString());
                            } else {
                                throw new ErrorException(ErrorType.DOCUMENTS_IS_INVALID);
                            }
                            break;
                    }
                } else {
                    throw new ErrorException(ErrorType.NO_AMENDMENTS);
                }
            } else {
                throw new ErrorException(ErrorType.INVALID_OWNER);
            }
            acEntity.setJsonData(jsonUtil.toJson(acDto));
            acRepository.save(acEntity);
            responseDto.setSuccess(true);
            responseDto.setData(responseDto);
        } else {
            throw new ErrorException(ErrorType.AC_NOT_FOUND);
        }

        return responseDto;
    }

    @Override
    public ResponseDto checkStatus(final String cpId, final String token) {
        final ACEntity acEntity = acRepository.getByCpIdAndAcId(cpId, UUID.fromString(token));
        final ResponseDto responseDto = new ResponseDto(null, null, null);
        if (acEntity != null) {
            responseDto.setSuccess(true);
            if (acEntity.getStatus()
                    .equals(ContractStatus.TERMINATED.toString())
                    || acEntity.getStatus()
                    .equals(ContractStatus.CANCELLED)) {
                responseDto.setData(new CheckAcRS(true));
            } else {
                responseDto.setData(new CheckAcRS(false));
            }
        } else {
            throw new ErrorException(ErrorType.AC_NOT_FOUND);
        }
        return responseDto;
    }


    private ACEntity convertACDtoToACEntity(final String cpId, final ACDto acDto) {

        final ACEntity acEntity = new ACEntity();
        acEntity.setCpId(cpId);
        acEntity.setAcId(UUID.fromString(acDto.getToken()));
        acEntity.setCanId(UUID.fromString(acDto.getContracts()
                .getExtendsContractId()));
        acEntity.setStatus(acDto.getContracts()
                .getStatus()
                .toString());

        acEntity.setStatusDetails(acDto.getContracts()
                .getStatusDetails()
                .toString());
        acEntity.setJsonData(jsonUtil.toJson(acDto));

        return acEntity;
    }

    private boolean isTitleOrDescriptionChanged(final ACDto acDto, final UpdateACRQ updateACRQ) {
        if (!acDto.getContracts()
                .getDescription()
                .equals(updateACRQ.getContracts()
                        .getDescription())
                || !acDto.getContracts()
                .getTitle()
                .equals(updateACRQ.getContracts()
                        .getTitle())) {
            return true;
        }
        return false;
    }

    private boolean isStatusPending(final ACEntity acEntity) {
        if (acEntity.getStatus()
                .equals(ContractStatus.PENDING.toString())) {
            return true;
        }
        return false;
    }

    private boolean isStatusActive(final ACEntity acEntity) {
        if (acEntity.getStatus()
                .equals(ContractStatus.ACTIVE.toString())) {
            return true;
        }
        return false;
    }

    private List<ContractDocumentDto> getNewDocumets(final List<ContractDocumentDto> savedDocs,
                                                     final List<ContractDocumentDto> inputDocs) {

        final List<ContractDocumentDto> newDocs = new ArrayList<>();

        for (int i = 0; i < inputDocs.size(); i++) {
            if (!savedDocs.contains(inputDocs.get(i))) {
                newDocs.add(inputDocs.get(i));
            }
        }
        return newDocs;
    }

    private boolean isValidDocumentsFromPending(final List<ContractDocumentDto> docs) {
        final List<ContractDocumentDto.DocumentType> validDocuments = new ArrayList<>();
        validDocuments.add(ContractDocumentDto.DocumentType.CONTRACT_NOTICE);
        validDocuments.add(ContractDocumentDto.DocumentType.CONTRACT_SIGNED);
        validDocuments.add(ContractDocumentDto.DocumentType.CONTRACT_SCHEDULE);
        validDocuments.add(ContractDocumentDto.DocumentType.CONTRACT_ANNEXE);
        validDocuments.add(ContractDocumentDto.DocumentType.CONTRACT_GUARANTEES);
        validDocuments.add(ContractDocumentDto.DocumentType.ILLUSTRATION);
        validDocuments.add(ContractDocumentDto.DocumentType.CONTRACT_SUMMARY);
        validDocuments.add(ContractDocumentDto.DocumentType.SUBMISSION_DOCUMENTS);

        for (int i = 0; i < docs.size(); i++) {
            if (!validDocuments.contains(docs.get(i)
                    .getDocumentType())) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidDocumentFromActive(final List<ContractDocumentDto> docs) {
        final List<ContractDocumentDto.DocumentType> validDocuments = new ArrayList<>();
        validDocuments.add(ContractDocumentDto.DocumentType.CONTRACT_SCHEDULE);
        validDocuments.add(ContractDocumentDto.DocumentType.ILLUSTRATION);
        validDocuments.add(ContractDocumentDto.DocumentType.CONTRACT_SUMMARY);

        for (int i = 0; i < docs.size(); i++) {
            if (!validDocuments.contains(docs.get(i)
                    .getDocumentType())) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidDocumentsFromStatusCancel(final List<ContractDocumentDto> docs) {
        final List<ContractDocumentDto.DocumentType> validDocuments = new ArrayList<>();
        validDocuments.add(ContractDocumentDto.DocumentType.CANCELLATION_DETAILS);
        validDocuments.add(ContractDocumentDto.DocumentType.CONFLICT_OF_INTEREST);

        for (int i = 0; i < docs.size(); i++) {
            if (!validDocuments.contains(docs.get(i)
                    .getDocumentType())) {
                return false;
            }
        }

        return true;
    }

    private boolean isValidDocumentsFromStatusComplete(final List<ContractDocumentDto> docs) {
        final List<ContractDocumentDto.DocumentType> validDocuments = new ArrayList<>();
        validDocuments.add(ContractDocumentDto.DocumentType.COMPLETION_CERTIFICATE);
        validDocuments.add(ContractDocumentDto.DocumentType.FINAL_AUDIT);

        for (int i = 0; i < docs.size(); i++) {
            if (!validDocuments.contains(docs.get(i)
                    .getDocumentType())) {
                return false;
            }
        }

        return true;
    }

    private boolean isValidDocumentsFromStatusUnsuccessful(final List<ContractDocumentDto> docs) {
        final List<ContractDocumentDto.DocumentType> validDocuments = new ArrayList<>();
        validDocuments.add(ContractDocumentDto.DocumentType.CONTRACT_ARRANGEMENTS);
        validDocuments.add(ContractDocumentDto.DocumentType.COMPLETION_CERTIFICATE);
        validDocuments.add(ContractDocumentDto.DocumentType.FINAL_AUDIT);
        validDocuments.add(ContractDocumentDto.DocumentType.CONFLICT_OF_INTEREST);
        validDocuments.add(ContractDocumentDto.DocumentType.CANCELLATION_DETAILS);

        for (int i = 0; i < docs.size(); i++) {
            if (!validDocuments.contains(docs.get(i)
                    .getDocumentType())) {
                return false;
            }
        }

        return true;
    }

    private boolean isDocumentTypePresent(final List<ContractDocumentDto> docs,
                                          final ContractDocumentDto.DocumentType documentType) {
        for (int i = 0; i < docs.size(); i++) {
            if (docs.get(i)
                    .getDocumentType() == documentType) {
                return true;
            }
        }
        return false;
    }

    private double sumBudgetSourceFromRequestDto(final UpdateACRQ updateACRQ) {
        double sumBudgetSources = 0;
        for (int i = 0; i < updateACRQ.getContracts()
                .getBudgetSource()
                .size(); i++) {
            sumBudgetSources += updateACRQ.getContracts()
                    .getBudgetSource()
                    .get(i)
                    .getAmount();
        }
        return sumBudgetSources;
    }
}
