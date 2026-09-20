package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.Contact;
import com.keenti.finances.domain.model.CreditAccountSettings;
import com.keenti.finances.domain.model.DashboardOverview;
import com.keenti.finances.domain.model.DashboardOverview.CurrentPosition;
import com.keenti.finances.domain.model.DashboardOverview.ExpectedMoney;
import com.keenti.finances.domain.model.DashboardOverview.PositionAccount;
import com.keenti.finances.domain.model.DashboardOverview.Section;
import com.keenti.finances.domain.model.DashboardOverview.YearHistory;
import com.keenti.finances.domain.model.Debt;
import com.keenti.finances.domain.model.FinancialAccount;
import com.keenti.finances.domain.model.MonthSummary;
import com.keenti.finances.domain.port.in.CreditStatementUseCase;
import com.keenti.finances.domain.port.in.SavingGoalUseCase;
import com.keenti.finances.domain.port.in.SpendingBudgetUseCase;
import com.keenti.finances.domain.port.out.BoxPlanRepository;
import com.keenti.finances.domain.port.out.BoxRepository;
import com.keenti.finances.domain.port.out.ContactRepository;
import com.keenti.finances.domain.port.out.CreditAccountSettingsRepository;
import com.keenti.finances.domain.port.out.DebtPaymentRepository;
import com.keenti.finances.domain.port.out.DebtRepository;
import com.keenti.finances.domain.port.out.FinancialAccountRepository;
import com.keenti.finances.domain.port.out.PaymentRecordRepository;
import com.keenti.finances.domain.port.out.SubscriptionRepository;
import com.keenti.finances.domain.port.out.TransactionRepository;
import com.keenti.finances.domain.port.out.UserTimeZoneProvider;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Section isolation, exercised against stub ports.
 *
 * <p>These are unit tests over the composition logic only: every dependency is
 * a stub, so what they establish is that one port throwing leaves the other
 * sections' figures intact and withholds only its own. They say nothing about
 * whether a real failed database read leaves the surrounding transaction usable
 * — that is a persistence question these stubs cannot answer.
 */
class DashboardOverviewServiceTest {

    private static final RuntimeException BOOM = new IllegalStateException("read failed");

    /* ---------------------------------------------------------------------
     * Scaffolding: one dynamic proxy per port, answering only what a test
     * declares. An undeclared call is an AssertionError, which section()
     * catches nothing of, so it surfaces as a test failure rather than as a
     * silently "unavailable" section.
     * ------------------------------------------------------------------ */

    private final Map<Class<?>, Map<String, Supplier<Object>>> answers = new HashMap<>();

    private void on(Class<?> port, String method, Supplier<Object> answer) {
        answers.computeIfAbsent(port, key -> new HashMap<>()).put(method, answer);
    }

    private void on(Class<?> port, String method, Object value) {
        on(port, method, () -> value);
    }

    private void throwing(Class<?> port, String method) {
        on(port, method, () -> {
            throw BOOM;
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> port) {
        Map<String, Supplier<Object>> declared =
            answers.getOrDefault(port, Map.of());
        return (T) Proxy.newProxyInstance(port.getClassLoader(), new Class<?>[] {port},
            (self, method, args) -> {
                switch (method.getName()) {
                    case "toString": return port.getSimpleName() + "-stub";
                    case "hashCode": return System.identityHashCode(self);
                    case "equals": return self == args[0];
                    default: break;
                }
                Supplier<Object> answer = declared.get(method.getName());
                if (answer == null) {
                    throw new AssertionError(
                        "unexpected call " + port.getSimpleName() + "." + method.getName());
                }
                return answer.get();
            });
    }

    private DashboardOverviewService service() {
        DashboardOverviewService service = new DashboardOverviewService();
        service.financialAccountRepository = proxy(FinancialAccountRepository.class);
        service.boxRepository = proxy(BoxRepository.class);
        service.boxPlanRepository = proxy(BoxPlanRepository.class);
        service.transactionRepository = proxy(TransactionRepository.class);
        service.creditAccountSettingsRepository = proxy(CreditAccountSettingsRepository.class);
        service.creditStatementUseCase = proxy(CreditStatementUseCase.class);
        service.subscriptionRepository = proxy(SubscriptionRepository.class);
        service.paymentRecordRepository = proxy(PaymentRecordRepository.class);
        service.debtRepository = proxy(DebtRepository.class);
        service.debtPaymentRepository = proxy(DebtPaymentRepository.class);
        service.contactRepository = proxy(ContactRepository.class);
        service.savingGoalUseCase = proxy(SavingGoalUseCase.class);
        service.spendingBudgetUseCase = proxy(SpendingBudgetUseCase.class);
        service.userTimeZoneProvider = proxy(UserTimeZoneProvider.class);
        return service;
    }

    /** A quiet but readable world: every section succeeds with nothing in it. */
    private void quietWorld() {
        on(UserTimeZoneProvider.class, "getTimeZone", ZoneId.of("America/Mexico_City"));
        on(UserTimeZoneProvider.class, "today", LocalDate.of(2024, 6, 15));

        on(FinancialAccountRepository.class, "isTrackingActive", true);
        on(FinancialAccountRepository.class, "isTrackingSetupRequired", false);
        on(FinancialAccountRepository.class, "findAll", List.of());
        on(FinancialAccountRepository.class, "getTotalBalance", money("0.00"));
        on(TransactionRepository.class, "getNetBalance", money("0.00"));
        on(TransactionRepository.class, "findMonthlySummary", List.of());
        on(BoxRepository.class, "getTotalBalance", money("0.00"));
        on(BoxRepository.class, "findAll", List.of());
        on(SubscriptionRepository.class, "findAll", List.of());
        on(DebtRepository.class, "findAll", List.of());
        on(DebtPaymentRepository.class, "sumByDebtIds", Map.of());
        on(ContactRepository.class, "findAll", List.of());
        on(PaymentRecordRepository.class, "findPendingContributions", List.of());
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    private static FinancialAccount account(long id, String name, String kind, String balance) {
        return new FinancialAccount(id, name, kind, 0, money("0.00"), LocalDate.of(2024, 1, 1),
            money(balance), false, null, null, 0L);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual, "expected " + expected + " but the figure was withheld");
        assertEquals(0, money(expected).compareTo(actual),
            "expected " + expected + " but was " + actual);
    }

    private static <T> T dataOf(Section<T> section) {
        assertEquals("ok", section.status(), "section was " + section.status());
        return section.data();
    }

    private static void assertUnavailable(Section<?> section) {
        assertEquals("unavailable", section.status());
        assertNull(section.data(), "an unavailable section must carry no data");
    }

    /* ---------------------------------------------------------------------
     * 1 — a failing account read does not take the year's history with it
     * ------------------------------------------------------------------ */

    @Test
    void failingAccountReadLeavesExplicitYearHistoryIntact() {
        quietWorld();
        throwing(FinancialAccountRepository.class, "findAll");
        on(TransactionRepository.class, "findMonthlySummary", List.of(
            new MonthSummary(1, money("1000.00"), money("400.00")),
            new MonthSummary(2, money("500.00"), money("100.00"))));

        DashboardOverview overview = service().getOverview(2024);

        assertUnavailable(overview.position());
        assertUnavailable(overview.attention());

        YearHistory history = dataOf(overview.history());
        assertEquals(2024, history.year());
        assertMoney("1500.00", history.totalIngress());
        assertMoney("500.00", history.totalEgress());
        assertEquals(2, history.monthly().size());
    }

    /* ---------------------------------------------------------------------
     * 2 — unreadable credit settings withhold capacity only
     * ------------------------------------------------------------------ */

    @Test
    void creditSettingsFailureKeepsBalancesAndWithholdsAvailableCredit() {
        quietWorld();
        on(FinancialAccountRepository.class, "findAll", List.of(
            account(1L, "Checking", "DEBIT", "1200.00"),
            account(2L, "Card", "CREDIT", "-300.00")));
        on(FinancialAccountRepository.class, "getTotalBalance", money("900.00"));
        on(BoxRepository.class, "getTotalBalance", money("250.00"));
        throwing(CreditAccountSettingsRepository.class, "findByAccountId");
        on(CreditStatementUseCase.class, "list", List.of());

        CurrentPosition position = dataOf(service().getOverview(2024).position());

        assertMoney("900.00", position.netBalance());
        assertMoney("250.00", position.inBoxes());
        assertMoney("650.00", position.availableToSpend());
        assertMoney("1200.00", position.moneyHeld());
        assertMoney("300.00", position.creditDebt());
        assertMoney("0.00", position.creditInFavor());
        assertTrue(position.creditLimitsPartial(), "the lost settings read must be declared");
        assertNull(position.availableCredit(), "capacity is withheld, not guessed");

        PositionAccount card = position.accounts().stream()
            .filter(row -> row.id() == 2L).findFirst().orElseThrow();
        assertNull(card.creditLimit());
        assertNull(card.availableCredit());
    }

    /* ---------------------------------------------------------------------
     * Optional — capacity aggregates across cards when every limit is readable
     * ------------------------------------------------------------------ */

    @Test
    void availableCreditSumsCapacityAndFloorsAnOverLimitCardAtZero() {
        quietWorld();
        on(FinancialAccountRepository.class, "findAll", List.of(
            account(1L, "Card A", "CREDIT", "-200.00"),
            account(2L, "Card B", "CREDIT", "-1500.00"),
            account(3L, "Card C", "CREDIT", "-50.00")));
        on(FinancialAccountRepository.class, "getTotalBalance", money("-1750.00"));
        on(BoxRepository.class, "getTotalBalance", money("0.00"));
        Map<Long, BigDecimal> limits = Map.of(1L, money("1000.00"), 2L, money("1000.00"));
        on(CreditAccountSettingsRepository.class, "findByAccountId", new Supplier<Object>() {
            private int call = 0;
            @Override public Object get() {
                // Calls arrive in account order; Card C has no configured limit.
                long id = ++call;
                return Optional.ofNullable(limits.get(id))
                    .map(limit -> new CreditAccountSettings(id, limit, 1, 15));
            }
        });
        on(CreditStatementUseCase.class, "list", List.of());

        CurrentPosition position = dataOf(service().getOverview(2024).position());

        assertFalse(position.creditLimitsPartial());
        // 800 from Card A, 0 from the over-limit Card B, nothing from Card C.
        assertMoney("800.00", position.availableCredit());
        assertMoney("1750.00", position.creditDebt());
    }

    /* ---------------------------------------------------------------------
     * 3 — a failed contribution read is not "nobody owes you anything"
     * ------------------------------------------------------------------ */

    @Test
    void contributionFailureKeepsDebtTotalAndWithholdsContributionFigure() {
        quietWorld();
        on(DebtRepository.class, "findAll", List.of(
            new Debt(1L, 10L, "INGRESS", "Loan to Ana", money("300.00"), "ACTIVE", null),
            new Debt(2L, null, "INGRESS", "Settled", money("100.00"), "ACTIVE", null),
            new Debt(3L, 10L, "INGRESS", "Closed", money("999.00"), "PAID", null),
            new Debt(4L, 10L, "EGRESS", "User owes", money("500.00"), "ACTIVE", null)));
        on(DebtPaymentRepository.class, "sumByDebtIds",
            Map.of(1L, money("100.00"), 2L, money("100.00")));
        on(ContactRepository.class, "findAll", List.of(new Contact(10L, "Ana", null, null)));
        throwing(PaymentRecordRepository.class, "findPendingContributions");

        ExpectedMoney expected = dataOf(service().getOverview(2024).expected());

        // 300 - 100 outstanding; the fully-paid and non-ACTIVE Debts drop out.
        assertMoney("200.00", expected.debtsOutstanding());
        assertEquals(1, expected.debtCount());
        assertEquals("Ana", expected.debts().get(0).contactName());

        assertFalse(expected.contributionsAvailable());
        assertNull(expected.contributionsOutstanding(),
            "a zero here would claim nobody owes this User anything");
        assertTrue(expected.contributions().isEmpty());
        assertTrue(expected.partial());
    }

    /* ---------------------------------------------------------------------
     * 4 — an unusable zone costs the day, not the position
     * ------------------------------------------------------------------ */

    @Test
    void zoneFailureWithNullYearKeepsPositionAndOnlyLosesHistory() {
        quietWorld();
        throwing(UserTimeZoneProvider.class, "getTimeZone");
        throwing(UserTimeZoneProvider.class, "today");
        on(FinancialAccountRepository.class, "findAll",
            List.of(account(1L, "Checking", "DEBIT", "400.00")));
        on(FinancialAccountRepository.class, "getTotalBalance", money("400.00"));
        on(BoxRepository.class, "getTotalBalance", money("150.00"));
        on(CreditStatementUseCase.class, "list", List.of());

        DashboardOverview overview;
        try {
            // No year: the current year has to come from the zone that just failed.
            overview = service().getOverview(null);
        } catch (RuntimeException e) {
            fail("an unusable zone must not fail the whole render: " + e);
            return;
        }

        assertNull(overview.today(), "an unusable zone is an absent day, never UTC");
        assertNull(overview.timeZone());

        CurrentPosition position = dataOf(overview.position());
        assertMoney("400.00", position.netBalance());
        assertMoney("150.00", position.inBoxes());
        assertMoney("250.00", position.availableToSpend());

        assertUnavailable(overview.history());
    }

    @Test
    void zoneFailureWithExplicitYearStillReportsThatYearsHistory() {
        quietWorld();
        throwing(UserTimeZoneProvider.class, "getTimeZone");
        throwing(UserTimeZoneProvider.class, "today");
        on(TransactionRepository.class, "findMonthlySummary",
            List.of(new MonthSummary(3, money("250.00"), money("50.00"))));

        DashboardOverview overview = service().getOverview(2023);

        assertNull(overview.today());
        YearHistory history = dataOf(overview.history());
        assertEquals(2023, history.year());
        assertMoney("250.00", history.totalIngress());
        assertMoney("50.00", history.totalEgress());
    }
}
