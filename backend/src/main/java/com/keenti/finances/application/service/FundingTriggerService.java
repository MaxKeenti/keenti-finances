package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.Category;
import com.keenti.finances.domain.model.FundingSuggestion;
import com.keenti.finances.domain.model.FundingSuggestionCalculator;
import com.keenti.finances.domain.model.FundingSuggestionSet;
import com.keenti.finances.domain.model.FundingTrigger;
import com.keenti.finances.domain.port.in.FundingTriggerUseCase;
import com.keenti.finances.domain.port.out.BoxPlanSuggestionPort;
import com.keenti.finances.domain.port.out.BoxRepository;
import com.keenti.finances.domain.port.out.CategoryRepository;
import com.keenti.finances.domain.port.out.FundingTriggerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class FundingTriggerService implements FundingTriggerUseCase {

    private static final Logger LOG = Logger.getLogger(FundingTriggerService.class);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    @Inject
    FundingTriggerRepository fundingTriggerRepository;

    @Inject
    BoxRepository boxRepository;

    @Inject
    CategoryRepository categoryRepository;

    @Inject
    BoxPlanSuggestionPort boxPlanSuggestionPort;

    @Override
    @Transactional
    public List<FundingTrigger> list(Long boxId) {
        requireActiveBox(boxId);
        return fundingTriggerRepository.findAllByBoxId(boxId);
    }

    @Override
    @Transactional
    public Optional<FundingTrigger> getById(Long boxId, Long triggerId) {
        requireActiveBox(boxId);
        return fundingTriggerRepository.findById(boxId, triggerId);
    }

    @Override
    @Transactional
    public FundingTrigger create(Long boxId, FundingTrigger requested) {
        requireActiveBox(boxId);
        if (requested == null) {
            throw new BadRequestException("Funding Trigger is required");
        }
        Category category = requireIngressCategory(requested.categoryId());
        FundingTrigger normalized = normalize(null, boxId, category, requested);
        ensurePlanStrategyCanResolve(normalized);
        ensureUnique(normalized.boxId(), normalized.categoryId(), null);
        FundingTrigger created = fundingTriggerRepository.save(normalized);
        LOG.infof("fundingTrigger.create id=%d boxId=%d categoryId=%d strategy=%s",
            created.id(), boxId, created.categoryId(), created.strategy());
        return created;
    }

    @Override
    @Transactional
    public FundingTrigger update(Long boxId, Long triggerId, FundingTrigger requested) {
        requireActiveBox(boxId);
        FundingTrigger existing = requireTrigger(boxId, triggerId);
        if (requested == null) {
            throw new BadRequestException("Funding Trigger is required");
        }
        Category category = requireIngressCategory(requested.categoryId());
        FundingTrigger normalized = normalize(triggerId, boxId, category, new FundingTrigger(
            requested.id(), requested.boxId(), requested.boxName(),
            requested.categoryId(), requested.categoryName(), requested.strategy(),
            requested.fixedAmount(), requested.percentage(), requested.enabled(),
            existing.createdAt(), existing.updatedAt()));
        ensurePlanStrategyCanResolve(normalized);
        ensureUnique(boxId, normalized.categoryId(), triggerId);
        FundingTrigger updated = fundingTriggerRepository.update(normalized);
        LOG.infof("fundingTrigger.update id=%d boxId=%d categoryId=%d strategy=%s enabled=%b",
            triggerId, boxId, updated.categoryId(), updated.strategy(), updated.enabled());
        return updated;
    }

    @Override
    @Transactional
    public FundingTrigger setEnabled(Long boxId, Long triggerId, boolean enabled) {
        requireActiveBox(boxId);
        FundingTrigger existing = requireTrigger(boxId, triggerId);
        FundingTrigger requested = new FundingTrigger(
            existing.id(), existing.boxId(), existing.boxName(),
            existing.categoryId(), existing.categoryName(), existing.strategy(),
            existing.fixedAmount(), existing.percentage(), enabled,
            existing.createdAt(), existing.updatedAt());
        if (enabled) {
            ensurePlanStrategyCanResolve(requested);
        }
        FundingTrigger updated = fundingTriggerRepository.update(requested);
        LOG.infof("fundingTrigger.enabled id=%d boxId=%d enabled=%b",
            triggerId, boxId, enabled);
        return updated;
    }

    @Override
    @Transactional
    public void delete(Long boxId, Long triggerId) {
        requireActiveBox(boxId);
        requireTrigger(boxId, triggerId);
        fundingTriggerRepository.delete(boxId, triggerId);
        LOG.infof("fundingTrigger.delete id=%d boxId=%d", triggerId, boxId);
    }

    @Override
    @Transactional
    public FundingSuggestionSet suggestions(Long categoryId, BigDecimal requestedIngressAmount) {
        requireIngressCategory(categoryId);
        BigDecimal ingressAmount = validMoney(requestedIngressAmount, "ingressAmount");
        List<FundingSuggestion> suggestions = new ArrayList<>();

        for (FundingTrigger trigger : fundingTriggerRepository.findEnabledByCategoryId(categoryId)) {
            suggestedAmount(trigger, ingressAmount)
                .filter(amount -> amount.signum() > 0)
                .ifPresent(amount -> suggestions.add(new FundingSuggestion(
                    trigger.id(), trigger.boxId(), trigger.boxName(),
                    trigger.strategy(), amount)));
        }

        BigDecimal combinedTotal = suggestions.stream()
            .map(FundingSuggestion::suggestedAmount)
            .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        LOG.infof("fundingTrigger.suggestions categoryId=%d count=%d total=%s",
            categoryId, suggestions.size(), combinedTotal);
        return new FundingSuggestionSet(
            categoryId, ingressAmount, suggestions, combinedTotal);
    }

    private Optional<BigDecimal> suggestedAmount(
            FundingTrigger trigger, BigDecimal ingressAmount) {
        Optional<BigDecimal> planDerived = trigger.strategy() == FundingTrigger.Strategy.PLAN_DERIVED
            ? boxPlanSuggestionPort.suggestedContribution(trigger.boxId())
            : Optional.empty();
        return FundingSuggestionCalculator.calculate(trigger, ingressAmount, planDerived);
    }

    private FundingTrigger normalize(
            Long id, Long boxId, Category category, FundingTrigger requested) {
        if (requested == null || requested.strategy() == null) {
            throw new BadRequestException("Funding Trigger strategy is required");
        }

        BigDecimal fixedAmount = requested.fixedAmount();
        BigDecimal percentage = requested.percentage();
        switch (requested.strategy()) {
            case PLAN_DERIVED -> {
                if (fixedAmount != null || percentage != null) {
                    throw new BadRequestException(
                        "PLAN_DERIVED does not accept a fixed amount or percentage");
                }
            }
            case FIXED_AMOUNT -> {
                if (percentage != null) {
                    throw new BadRequestException("FIXED_AMOUNT does not accept a percentage");
                }
                fixedAmount = validMoney(fixedAmount, "fixedAmount");
            }
            case PERCENTAGE -> {
                if (fixedAmount != null) {
                    throw new BadRequestException("PERCENTAGE does not accept a fixed amount");
                }
                percentage = validPercentage(percentage);
            }
        }

        return new FundingTrigger(
            id, boxId, null, category.getId(), category.getName(),
            requested.strategy(), fixedAmount, percentage, requested.enabled(),
            requested.createdAt(), requested.updatedAt());
    }

    private BigDecimal validMoney(BigDecimal amount, String fieldName) {
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException(fieldName + " must be greater than zero");
        }
        BigDecimal stripped = amount.stripTrailingZeros();
        if (stripped.scale() > 2) {
            throw new BadRequestException(fieldName + " supports at most two decimal places");
        }
        int integerDigits = Math.max(stripped.precision() - stripped.scale(), 0);
        if (integerDigits > 10) {
            throw new BadRequestException(fieldName + " is too large");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private BigDecimal validPercentage(BigDecimal percentage) {
        if (percentage == null || percentage.signum() <= 0
                || percentage.compareTo(ONE_HUNDRED) > 0) {
            throw new BadRequestException("percentage must be greater than zero and at most 100");
        }
        BigDecimal stripped = percentage.stripTrailingZeros();
        if (stripped.scale() > 4) {
            throw new BadRequestException("percentage supports at most four decimal places");
        }
        return percentage.setScale(4, RoundingMode.UNNECESSARY);
    }

    private void ensurePlanStrategyCanResolve(FundingTrigger trigger) {
        if (trigger.strategy() == FundingTrigger.Strategy.PLAN_DERIVED
                && boxPlanSuggestionPort.suggestedContribution(trigger.boxId()).isEmpty()) {
            throw new BadRequestException("PLAN_DERIVED requires an active Box Plan");
        }
    }

    private void ensureUnique(Long boxId, Long categoryId, Long excludingId) {
        if (fundingTriggerRepository.existsByBoxAndCategory(
                boxId, categoryId, excludingId)) {
            throw conflict("A Funding Trigger already exists for this Box and Category");
        }
    }

    private void requireActiveBox(Long boxId) {
        boxRepository.findActiveById(boxId).orElseThrow(() ->
            new NotFoundException("Box not found: " + boxId));
    }

    private Category requireIngressCategory(Long categoryId) {
        if (categoryId == null) {
            throw new BadRequestException("categoryId is required");
        }
        Category category = categoryRepository.findById(categoryId).orElseThrow(() ->
            new NotFoundException("Category not found: " + categoryId));
        if (!"INGRESS".equals(category.getType()) && !"BOTH".equals(category.getType())) {
            throw new BadRequestException("Funding Triggers require an INGRESS-capable Category");
        }
        return category;
    }

    private FundingTrigger requireTrigger(Long boxId, Long triggerId) {
        return fundingTriggerRepository.findById(boxId, triggerId).orElseThrow(() ->
            new NotFoundException("Funding Trigger not found: " + triggerId));
    }

    private WebApplicationException conflict(String message) {
        return new WebApplicationException(
            Response.status(Response.Status.CONFLICT)
                .entity("{\"error\":\"" + message + "\"}")
                .build());
    }
}
