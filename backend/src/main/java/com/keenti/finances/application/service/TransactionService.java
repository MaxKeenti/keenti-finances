package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.BoxDistribution;
import com.keenti.finances.domain.model.BoxFunding;
import com.keenti.finances.domain.model.PagedResult;
import com.keenti.finances.domain.model.TrashItem;
import com.keenti.finances.domain.model.Transaction;
import com.keenti.finances.domain.port.in.TransactionUseCase;
import com.keenti.finances.domain.port.out.BoxDistributionRepository;
import com.keenti.finances.domain.port.out.BoxFundingRepository;
import com.keenti.finances.domain.port.out.BoxRepository;
import com.keenti.finances.domain.port.out.SubscriptionRepository;
import com.keenti.finances.domain.port.out.TransactionRepository;
import com.keenti.finances.domain.port.out.UserTimeZoneProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TransactionService implements TransactionUseCase {

    private static final Logger LOG = Logger.getLogger(TransactionService.class);
    private static final Set<String> VALID_DIRECTIONS = Set.of("INGRESS", "EGRESS");

    @Inject
    TransactionRepository transactionRepository;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    BoxRepository boxRepository;

    @Inject
    BoxFundingRepository boxFundingRepository;

    @Inject
    BoxDistributionRepository boxDistributionRepository;

    @Inject
    UserTimeZoneProvider userTimeZoneProvider;

    @Override
    public List<Transaction> list() {
        List<Transaction> transactions = transactionRepository.findAll().stream()
            .map(this::withBoxAllocations)
            .toList();
        LOG.infof("transaction.list count=%d", transactions.size());
        return transactions;
    }

    @Override
    public PagedResult<Transaction> listPage(int pageIndex, int pageSize, String sortBy, boolean descending) {
        PagedResult<Transaction> page = transactionRepository.findPage(pageIndex, pageSize, sortBy, descending);
        page = new PagedResult<>(
            page.items().stream().map(this::withBoxAllocations).toList(),
            page.pageIndex(), page.pageSize(), page.totalItems(), page.totalPages());
        LOG.infof(
            "transaction.listPage pageIndex=%d pageSize=%d sortBy=%s descending=%b total=%d",
            pageIndex, pageSize, sortBy, descending, page.totalItems()
        );
        return page;
    }

    @Override
    public Optional<Transaction> getById(Long id) {
        Optional<Transaction> result = transactionRepository.findById(id).map(this::withBoxAllocations);
        LOG.infof("transaction.get id=%d found=%b", id, result.isPresent());
        return result;
    }

    @Override
    @Transactional
    public Transaction create(Transaction transaction) {
        List<BoxFunding> funding = normalizeAndValidateFunding(transaction);
        List<BoxDistribution> distributions = normalizeAndValidateDistributions(transaction);
        validateFundingTransition(
            List.of(), false, transaction.getTransactionDate(),
            funding, true, transaction.getTransactionDate());
        lockDistributionBoxes(distributions);
        LocalDateTime fundingCreatedAt = LocalDateTime.now();
        validateDebits(funding, transaction.getTransactionDate(), fundingCreatedAt);

        Transaction created = transactionRepository.save(transaction);
        boxFundingRepository.saveForTransaction(
            created.getId(), created.getTransactionDate(), fundingCreatedAt, funding);
        if (!distributions.isEmpty()) {
            boxFundingRepository.flush();
            validatePostIngressAvailableToSpend(distributions);
            boxDistributionRepository.saveForTransaction(
                created.getId(), created.getTransactionDate(),
                LocalDateTime.now(), distributions);
        }
        Transaction result = withBoxAllocations(created);
        LOG.infof(
            "transaction.create id=%d amount=%s direction=%s boxFundingCount=%d boxDistributionCount=%d",
            result.getId(), result.getAmount(), result.getDirection(),
            result.getBoxFunding().size(), result.getBoxDistributions().size());
        return result;
    }

    @Override
    @Transactional
    public Transaction update(Long id, Transaction transaction) {
        Transaction existing = transactionRepository.findById(id).orElseThrow(() ->
            new NotFoundException("Transaction not found: " + id));
        if (!transaction.getBoxDistributions().isEmpty()) {
            throw new BadRequestException(
                "Applied Box distributions are independent and cannot be changed with the Transaction");
        }
        List<BoxFunding> oldFunding = boxFundingRepository.findByTransactionId(id);
        List<BoxFunding> newFunding = normalizeAndValidateFunding(transaction);

        validateFundingTransition(
            oldFunding, true, existing.getTransactionDate(),
            newFunding, true, transaction.getTransactionDate());

        boolean fundingChanges = !sameFunding(oldFunding, newFunding)
            || !existing.getTransactionDate().equals(transaction.getTransactionDate());
        LocalDateTime fundingCreatedAt = LocalDateTime.now();
        if (fundingChanges) {
            boxFundingRepository.deleteForTransaction(id);
            boxFundingRepository.flush();
            validateDebits(newFunding, transaction.getTransactionDate(), fundingCreatedAt);
        }

        Transaction updated = transactionRepository.update(new Transaction(
            id, transaction.getAmount(), transaction.getDirection(), transaction.getDescription(),
            transaction.getTransactionDate(), transaction.getCategoryId(), transaction.getContactId(),
            existing.getSubscriptionId(), newFunding));
        if (fundingChanges) {
            boxFundingRepository.saveForTransaction(
                id, transaction.getTransactionDate(), fundingCreatedAt, newFunding);
        }
        if (sourceTransactionChanged(existing, transaction)) {
            boxDistributionRepository.markSourceChanged(id);
        }
        Transaction result = withBoxAllocations(updated);
        LOG.infof("transaction.update id=%d boxFundingCount=%d", id, result.getBoxFunding().size());
        return result;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Transaction existing = transactionRepository.findById(id).orElseThrow(() ->
            new NotFoundException("Transaction not found: " + id));
        List<BoxFunding> funding = boxFundingRepository.findByTransactionId(id);
        validateFundingTransition(
            funding, true, existing.getTransactionDate(),
            List.of(), false, existing.getTransactionDate());
        transactionRepository.softDeleteById(id);
        LOG.infof("transaction.soft_deleted id=%d", id);
    }

    @Override
    @Transactional
    public void restore(Long id) {
        Transaction deleted = transactionRepository.findDeletedTransactionById(id).orElseThrow(() ->
            new NotFoundException("Deleted transaction not found: " + id));
        List<BoxFunding> funding = boxFundingRepository.findByTransactionId(id);
        validateFundingTransition(
            List.of(), false, deleted.getTransactionDate(),
            funding, true, deleted.getTransactionDate());
        for (BoxFunding line : funding) {
            LocalDateTime eventTimestamp = line.createdAt() != null
                ? line.createdAt()
                : LocalDateTime.now();
            LocalDate effectiveDate = line.effectiveDate() != null
                ? line.effectiveDate()
                : deleted.getTransactionDate();
            if (!boxRepository.canDebit(
                    line.boxId(), line.amount(), effectiveDate, eventTimestamp)) {
                throw conflict("Box has insufficient historical balance for funding: " + line.boxId());
            }
        }
        transactionRepository.restoreById(id);
        LOG.infof("transaction.restored id=%d", id);
    }

    @Override
    @Transactional
    public void permanentDelete(Long id) {
        transactionRepository.findDeletedById(id).orElseThrow(() ->
            new NotFoundException("Deleted transaction not found: " + id));
        transactionRepository.deleteById(id);
        LOG.infof("transaction.permanent_deleted id=%d", id);
    }

    @Override
    public List<TrashItem> listDeleted() {
        List<TrashItem> items = transactionRepository.findAllDeleted();
        LOG.infof("transaction.trash.list count=%d", items.size());
        return items;
    }

    @Override
    @Transactional
    public Transaction linkSubscription(Long transactionId, Long subscriptionId) {
        Transaction existing = transactionRepository.findById(transactionId).orElseThrow(() ->
            new NotFoundException("Transaction not found: " + transactionId));
        if (subscriptionId != null) {
            if (!"EGRESS".equals(existing.getDirection())) {
                throw new BadRequestException("Only EGRESS transactions can be linked to subscriptions");
            }
            subscriptionRepository.findById(subscriptionId).orElseThrow(() ->
                new NotFoundException("Subscription not found: " + subscriptionId));
        }
        Transaction updated = transactionRepository.update(new Transaction(
            existing.getId(), existing.getAmount(), existing.getDirection(), existing.getDescription(),
            existing.getTransactionDate(), existing.getCategoryId(), existing.getContactId(),
            subscriptionId));
        LOG.infof("transaction.link transactionId=%d subscriptionId=%s", transactionId,
            subscriptionId != null ? subscriptionId.toString() : "null");
        return withBoxAllocations(updated);
    }

    @Override
    public List<Transaction> listBySubscriptionId(Long subscriptionId) {
        subscriptionRepository.findById(subscriptionId).orElseThrow(() ->
            new NotFoundException("Subscription not found: " + subscriptionId));
        List<Transaction> transactions = transactionRepository.findBySubscriptionId(subscriptionId).stream()
            .map(this::withBoxAllocations)
            .toList();
        LOG.infof("transaction.listBySubscription subscriptionId=%d count=%d", subscriptionId, transactions.size());
        return transactions;
    }

    private List<BoxFunding> normalizeAndValidateFunding(Transaction transaction) {
        if (!VALID_DIRECTIONS.contains(transaction.getDirection())) {
            throw new BadRequestException(
                "Invalid direction: " + transaction.getDirection() + ". Must be INGRESS or EGRESS");
        }
        if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Transaction amount must be positive");
        }

        List<BoxFunding> requested = transaction.getBoxFunding() == null
            ? List.of()
            : transaction.getBoxFunding();
        if (!requested.isEmpty() && !"EGRESS".equals(transaction.getDirection())) {
            throw new BadRequestException("Box funding applies only to EGRESS Transactions");
        }
        if (!requested.isEmpty()
                && transaction.getTransactionDate().isAfter(userTimeZoneProvider.today())) {
            throw new BadRequestException("Box funding cannot be future-dated");
        }

        Set<Long> boxIds = new HashSet<>();
        List<BoxFunding> normalized = new ArrayList<>(requested.size());
        BigDecimal total = BigDecimal.ZERO;
        for (int index = 0; index < requested.size(); index++) {
            BoxFunding line = requested.get(index);
            if (line == null || line.boxId() == null) {
                throw new BadRequestException("Every Box funding line requires a Box");
            }
            if (line.amount() == null || line.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Every Box funding amount must be positive");
            }
            if (line.amount().stripTrailingZeros().scale() > 2) {
                throw new BadRequestException("Box funding amounts support at most two decimal places");
            }
            if (!boxIds.add(line.boxId())) {
                throw new BadRequestException("A Box may appear only once in Transaction funding");
            }
            normalized.add(new BoxFunding(line.boxId(), line.amount(), index));
            total = total.add(line.amount());
        }
        if (total.compareTo(transaction.getAmount()) > 0) {
            throw new BadRequestException("Box funding cannot exceed the Transaction amount");
        }
        return List.copyOf(normalized);
    }

    private List<BoxDistribution> normalizeAndValidateDistributions(Transaction transaction) {
        List<BoxDistribution> requested = transaction.getBoxDistributions() == null
            ? List.of()
            : transaction.getBoxDistributions();
        if (requested.isEmpty()) {
            return List.of();
        }
        if (!"INGRESS".equals(transaction.getDirection())) {
            throw new BadRequestException("Box distributions apply only to INGRESS Transactions");
        }
        if (transaction.getTransactionDate().isAfter(userTimeZoneProvider.today())) {
            throw new BadRequestException("Box distributions cannot be future-dated");
        }

        Set<Long> boxIds = new HashSet<>();
        List<BoxDistribution> normalized = new ArrayList<>(requested.size());
        for (int index = 0; index < requested.size(); index++) {
            BoxDistribution line = requested.get(index);
            if (line == null || line.boxId() == null) {
                throw new BadRequestException("Every Box distribution requires a Box");
            }
            if (line.amount() == null || line.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Every Box distribution amount must be positive");
            }
            if (line.amount().stripTrailingZeros().scale() > 2) {
                throw new BadRequestException("Box distribution amounts support at most two decimal places");
            }
            if (!boxIds.add(line.boxId())) {
                throw new BadRequestException("A Box may appear only once in an INGRESS distribution");
            }
            normalized.add(new BoxDistribution(
                line.boxId(), line.amount().setScale(2, RoundingMode.UNNECESSARY), index));
        }
        return List.copyOf(normalized);
    }

    private void lockDistributionBoxes(List<BoxDistribution> distributions) {
        if (distributions.isEmpty()) {
            return;
        }
        boxRepository.lockAllocationScope();
        for (Long boxId : distributions.stream()
                .map(BoxDistribution::boxId)
                .sorted()
                .toList()) {
            boxRepository.lockActiveById(boxId).orElseThrow(() ->
                new NotFoundException("Box not found: " + boxId));
        }
    }

    private void validatePostIngressAvailableToSpend(List<BoxDistribution> distributions) {
        BigDecimal distributionTotal = distributions.stream()
            .map(BoxDistribution::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal availableToSpend = transactionRepository.getNetBalance()
            .subtract(boxRepository.getTotalBalance());
        if (distributionTotal.compareTo(availableToSpend) > 0) {
            throw new BadRequestException(
                "Box distributions exceed Available to Spend after the INGRESS Transaction");
        }
    }

    private boolean sourceTransactionChanged(Transaction existing, Transaction requested) {
        return existing.getAmount().compareTo(requested.getAmount()) != 0
            || !Objects.equals(existing.getDirection(), requested.getDirection())
            || !Objects.equals(existing.getDescription(), requested.getDescription())
            || !Objects.equals(existing.getTransactionDate(), requested.getTransactionDate())
            || !Objects.equals(existing.getCategoryId(), requested.getCategoryId())
            || !Objects.equals(existing.getContactId(), requested.getContactId());
    }

    /**
     * Locks every affected Box in a stable order and checks ownership and the
     * archived-Box correction safeguard. Historical debit validation runs only
     * after an update has transactionally removed its previous funding rows.
     */
    private void validateFundingTransition(
            List<BoxFunding> oldFunding, boolean oldApplied, LocalDate oldDate,
            List<BoxFunding> newFunding, boolean newApplied, LocalDate newDate) {
        Map<Long, BigDecimal> oldAmounts = amountsByBox(oldFunding, oldApplied);
        Map<Long, BigDecimal> newAmounts = amountsByBox(newFunding, newApplied);
        Set<Long> affectedIds = new HashSet<>(oldAmounts.keySet());
        affectedIds.addAll(newAmounts.keySet());

        List<Long> orderedIds = affectedIds.stream().sorted().toList();
        for (Long boxId : orderedIds) {
            var box = boxRepository.lockByIdIncludingArchived(boxId).orElseThrow(() ->
                new NotFoundException("Box not found: " + boxId));

            BigDecimal oldAmount = oldAmounts.getOrDefault(boxId, BigDecimal.ZERO);
            BigDecimal newAmount = newAmounts.getOrDefault(boxId, BigDecimal.ZERO);
            boolean amountChanges = oldAmount.compareTo(newAmount) != 0;
            boolean dateChanges = oldAmount.signum() > 0
                && newAmount.signum() > 0
                && !oldDate.equals(newDate);

            if ((amountChanges || dateChanges) && box.isArchived()) {
                throw conflict("Box must be restored before changing its funding: " + boxId);
            }
        }
    }

    private void validateDebits(List<BoxFunding> funding, LocalDate effectiveDate,
                                LocalDateTime createdAt) {
        for (BoxFunding line : funding) {
            if (!boxRepository.canDebit(line.boxId(), line.amount(), effectiveDate, createdAt)) {
                throw conflict("Box has insufficient historical balance for funding: " + line.boxId());
            }
        }
    }

    private Map<Long, BigDecimal> amountsByBox(List<BoxFunding> funding, boolean applied) {
        if (!applied) {
            return Map.of();
        }
        Map<Long, BigDecimal> amounts = new LinkedHashMap<>();
        for (BoxFunding line : funding) {
            amounts.put(line.boxId(), line.amount());
        }
        return amounts;
    }

    private boolean sameFunding(List<BoxFunding> left, List<BoxFunding> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            BoxFunding a = left.get(index);
            BoxFunding b = right.get(index);
            if (!a.boxId().equals(b.boxId()) || a.amount().compareTo(b.amount()) != 0) {
                return false;
            }
        }
        return true;
    }

    private Transaction withBoxAllocations(Transaction transaction) {
        return transaction
            .withBoxFunding(boxFundingRepository.findByTransactionId(transaction.getId()))
            .withBoxDistributions(
                boxDistributionRepository.findByTransactionId(transaction.getId()));
    }

    private WebApplicationException conflict(String message) {
        return new WebApplicationException(
            Response.status(Response.Status.CONFLICT)
                .entity("{\"error\":\"" + message + "\"}")
                .build());
    }
}
