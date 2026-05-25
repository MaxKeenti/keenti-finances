package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.Subscription;
import com.keenti.finances.domain.model.SubscriptionMember;
import com.keenti.finances.domain.model.TrashItem;
import com.keenti.finances.domain.port.in.SubscriptionUseCase;
import com.keenti.finances.domain.port.out.SubscriptionMemberRepository;
import com.keenti.finances.domain.port.out.SubscriptionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SubscriptionService implements SubscriptionUseCase {

    private static final Logger LOG = Logger.getLogger(SubscriptionService.class);
    private static final Set<String> VALID_CYCLES = Set.of("MONTHLY", "YEARLY");
    private static final Set<String> VALID_TYPES = Set.of("PERSONAL", "SHARED");

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    SubscriptionMemberRepository subscriptionMemberRepository;

    @Override
    public List<Subscription> list() {
        List<Subscription> subs = subscriptionRepository.findAll();
        LOG.infof("subscription.list count=%d", subs.size());
        return subs;
    }

    @Override
    public Optional<Subscription> getById(Long id) {
        Optional<Subscription> result = subscriptionRepository.findById(id);
        LOG.infof("subscription.get id=%d found=%b", id, result.isPresent());
        return result;
    }

    @Override
    @Transactional
    public Subscription create(Subscription subscription) {
        validate(subscription);
        String token = "SHARED".equals(subscription.getType()) ? UUID.randomUUID().toString() : null;
        Subscription toSave = new Subscription(
            null, subscription.getName(), subscription.getCost(),
            subscription.getBillingCycle(), subscription.getType(),
            subscription.getCategoryId(), subscription.getNextBillingDate(),
            token, LocalDateTime.now(), subscription.isOwnerParticipates()
        );
        Subscription created = subscriptionRepository.save(toSave);
        LOG.infof("subscription.create id=%d name=%s type=%s", created.getId(), created.getName(), created.getType());
        return created;
    }

    @Override
    @Transactional
    public Subscription update(Long id, Subscription subscription) {
        validate(subscription);
        Subscription existing = subscriptionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Subscription not found: " + id));
        // Preserve token on type change: if was SHARED keep token; if changing to SHARED and had none, generate
        String token = existing.getTokenUuid();
        if ("SHARED".equals(subscription.getType()) && token == null) {
            token = UUID.randomUUID().toString();
        } else if ("PERSONAL".equals(subscription.getType())) {
            token = null;
        }
        boolean ownerParticipatesChanged = existing.isOwnerParticipates() != subscription.isOwnerParticipates();
        Subscription updated = subscriptionRepository.update(new Subscription(
            id, subscription.getName(), subscription.getCost(),
            subscription.getBillingCycle(), subscription.getType(),
            subscription.getCategoryId(), subscription.getNextBillingDate(),
            token, existing.getCreatedAt(), subscription.isOwnerParticipates()
        ));
        LOG.infof("subscription.update id=%d", id);
        if (ownerParticipatesChanged && "SHARED".equals(updated.getType())) {
            recalculateShares(id, updated);
        }
        return updated;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        subscriptionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Subscription not found: " + id));
        subscriptionRepository.softDeleteById(id);
        LOG.infof("subscription.soft_deleted id=%d", id);
    }

    @Override
    @Transactional
    public void restore(Long id) {
        subscriptionRepository.findDeletedById(id).orElseThrow(() ->
            new NotFoundException("Deleted subscription not found: " + id));
        subscriptionRepository.restoreById(id);
        LOG.infof("subscription.restored id=%d", id);
    }

    @Override
    @Transactional
    public void permanentDelete(Long id) {
        subscriptionRepository.findDeletedById(id).orElseThrow(() ->
            new NotFoundException("Deleted subscription not found: " + id));
        subscriptionRepository.deleteById(id);
        LOG.infof("subscription.permanent_deleted id=%d", id);
    }

    @Override
    public List<TrashItem> listDeleted() {
        List<TrashItem> items = subscriptionRepository.findAllDeleted();
        LOG.infof("subscription.trash.list count=%d", items.size());
        return items;
    }

    @Override
    @Transactional
    public SubscriptionMember addMember(Long subscriptionId, Long contactId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new NotFoundException("Subscription not found: " + subscriptionId));
        if (!"SHARED".equals(sub.getType())) {
            throw new BadRequestException("Cannot add members to a PERSONAL subscription");
        }
        List<SubscriptionMember> existing = subscriptionMemberRepository.findBySubscriptionId(subscriptionId);
        boolean duplicate = existing.stream().anyMatch(m -> m.getContactId().equals(contactId));
        if (duplicate) {
            throw new jakarta.ws.rs.WebApplicationException(
                jakarta.ws.rs.core.Response.status(409)
                    .entity("{\"error\":\"Contact is already a member of this subscription\"}")
                    .build());
        }
        int memberCount = existing.size() + 1;
        int divisor = memberCount + (sub.isOwnerParticipates() ? 1 : 0);
        BigDecimal share = sub.getCost().divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
        subscriptionMemberRepository.updateShareAmounts(subscriptionId, share);
        SubscriptionMember member = subscriptionMemberRepository.save(
            new SubscriptionMember(null, subscriptionId, contactId, share, LocalDateTime.now()));
        LOG.infof("subscription.member.add subscriptionId=%d contactId=%d share=%s divisor=%d ownerParticipates=%b",
            subscriptionId, contactId, share, divisor, sub.isOwnerParticipates());
        return member;
    }

    @Override
    @Transactional
    public void removeMember(Long subscriptionId, Long memberId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new NotFoundException("Subscription not found: " + subscriptionId));
        subscriptionMemberRepository.findById(memberId)
            .orElseThrow(() -> new NotFoundException("Member not found: " + memberId));
        subscriptionMemberRepository.deleteById(memberId);
        List<SubscriptionMember> remaining = subscriptionMemberRepository.findBySubscriptionId(subscriptionId);
        if (!remaining.isEmpty()) {
            int divisor = remaining.size() + (sub.isOwnerParticipates() ? 1 : 0);
            BigDecimal share = sub.getCost().divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
            subscriptionMemberRepository.updateShareAmounts(subscriptionId, share);
            LOG.infof("subscription.member.remove subscriptionId=%d memberId=%d newShare=%s divisor=%d ownerParticipates=%b",
                subscriptionId, memberId, share, divisor, sub.isOwnerParticipates());
        } else {
            LOG.infof("subscription.member.remove subscriptionId=%d memberId=%d noRemainingMembers", subscriptionId, memberId);
        }
    }

    @Override
    public List<SubscriptionMember> listMembers(Long subscriptionId) {
        List<SubscriptionMember> members = subscriptionMemberRepository.findBySubscriptionId(subscriptionId);
        LOG.infof("subscription.member.list subscriptionId=%d count=%d", subscriptionId, members.size());
        return members;
    }

    @Override
    public Optional<Subscription> getByToken(String tokenUuid) {
        Optional<Subscription> result = subscriptionRepository.findByTokenUuid(tokenUuid);
        LOG.infof("subscription.token.lookup token=%s found=%b", tokenUuid, result.isPresent());
        return result;
    }

    @Override
    @Transactional
    public void recalculateShares(Long subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new NotFoundException("Subscription not found: " + subscriptionId));
        recalculateShares(subscriptionId, sub);
    }

    private void recalculateShares(Long subscriptionId, Subscription sub) {
        List<SubscriptionMember> members = subscriptionMemberRepository.findBySubscriptionId(subscriptionId);
        if (members.isEmpty()) {
            return;
        }
        int oldDivisor = members.size(); // before: no owner flag awareness
        int newDivisor = members.size() + (sub.isOwnerParticipates() ? 1 : 0);
        BigDecimal share = sub.getCost().divide(BigDecimal.valueOf(newDivisor), 2, RoundingMode.HALF_UP);
        subscriptionMemberRepository.updateShareAmounts(subscriptionId, share);
        LOG.infof("subscription.shares.recalculate subscriptionId=%d oldDivisor=%d newDivisor=%d newShare=%s ownerParticipates=%b",
            subscriptionId, oldDivisor, newDivisor, share, sub.isOwnerParticipates());
    }

    private void validate(Subscription s) {
        if (!VALID_CYCLES.contains(s.getBillingCycle())) {
            throw new BadRequestException("Invalid billing_cycle: " + s.getBillingCycle() + ". Must be MONTHLY or YEARLY");
        }
        if (!VALID_TYPES.contains(s.getType())) {
            throw new BadRequestException("Invalid type: " + s.getType() + ". Must be PERSONAL or SHARED");
        }
    }
}
