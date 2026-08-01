export type PlanCadence = 'DAILY' | 'WEEKLY' | 'MONTHLY';
export type BoxPlanType = 'SAVING_GOAL' | 'SPENDING_BUDGET';
export type SavingGoalStatus =
	| 'ACTIVE'
	| 'READY_TO_COMPLETE'
	| 'OVERDUE'
	| 'COMPLETED'
	| 'ABANDONED';
export type SpendingBudgetStatus = 'ACTIVE' | 'ENDED';
export type BoxPlanStatus = SavingGoalStatus | SpendingBudgetStatus;

export type BoxPlanSummary = {
	id: number;
	boxId: number;
	type: BoxPlanType;
	status: BoxPlanStatus;
	createdAt: string;
	closedAt: string | null;
	completionAmount: number | null;
};

export type SavingGoalPeriod = {
	id: number;
	revisionId: number;
	startDate: string;
	endDate: string;
	openingBalance: number;
	closingBalance: number;
	netProgress: number;
	regularCommitment: number;
	openingArrears: number;
	requiredAmount: number;
	arrearsCovered: number;
	regularProgress: number;
	extraProgress: number;
	shortfall: number;
	status: 'OPEN' | 'ACHIEVED' | 'MISSED';
	evaluatedAt: string | null;
};

export type SavingGoalRevision = {
	id: number;
	effectiveFrom: string;
	cadence: PlanCadence;
	anchorWeekday: number | null;
	anchorDayOfMonth: number | null;
	targetAmount: number;
	targetDate: string;
	regularCommitment: number;
	createdAt: string;
	supersededAt: string | null;
	scheduled: boolean;
};

export type SavingGoal = {
	id: number;
	boxId: number;
	type: 'SAVING_GOAL';
	status: SavingGoalStatus;
	targetAmount: number;
	targetDate: string;
	cadence: PlanCadence;
	anchorWeekday: number | null;
	anchorDayOfMonth: number | null;
	regularCommitment: number;
	boxBalance: number;
	remainingAmount: number;
	progressPercent: number;
	arrears: number;
	currentCommitment: number;
	projectedCompletionDate: string | null;
	suggestedExtensionDate: string | null;
	currentPeriod: SavingGoalPeriod | null;
	periods: SavingGoalPeriod[];
	revisions: SavingGoalRevision[];
	createdAt: string;
	updatedAt: string;
	closedAt: string | null;
	completionAmount: number | null;
};

export type SavingGoalRevisionPreview = {
	effectiveFrom: string;
	targetAmount: number;
	targetDate: string;
	cadence: PlanCadence;
	anchorWeekday: number | null;
	anchorDayOfMonth: number | null;
	regularCommitment: number;
	remainingPeriods: number;
	boxBalance: number;
	remainingAmount: number;
	currentArrears: number;
	projectedCompletionDate: string | null;
	suggestedExtensionDate: string | null;
};

export type SpendingBudgetPeriod = {
	id: number;
	revisionId: number;
	periodStart: string;
	periodEnd: string;
	openingBalance: number;
	closingBalance: number;
	netProgress: number;
	deposits: number;
	withdrawals: number;
	transfersIn: number;
	transfersOut: number;
	fundedSpending: number;
	suggestedTopUp: number;
	evaluatedAt: string | null;
};

export type SpendingBudgetRevision = {
	id: number;
	effectiveFrom: string;
	cadence: PlanCadence;
	anchorWeekday: number | null;
	anchorDayOfMonth: number | null;
	desiredBalance: number;
	createdAt: string;
	supersededAt: string | null;
	scheduled: boolean;
};

export type SpendingBudget = {
	id: number;
	boxId: number;
	type: 'SPENDING_BUDGET';
	status: SpendingBudgetStatus;
	desiredBalance: number;
	cadence: PlanCadence;
	anchorWeekday: number | null;
	anchorDayOfMonth: number | null;
	boxBalance: number;
	suggestedTopUp: number;
	currentPeriod: SpendingBudgetPeriod;
	periods: SpendingBudgetPeriod[];
	revisions: SpendingBudgetRevision[];
	createdAt: string;
	updatedAt: string;
	closedAt: string | null;
	completionAmount: number | null;
};

export type SpendingBudgetRevisionPreview = {
	planId: number;
	effectiveFrom: string;
	cadence: PlanCadence;
	anchorWeekday: number | null;
	anchorDayOfMonth: number | null;
	desiredBalance: number;
	currentBalance: number;
	suggestedTopUp: number;
};

export type BoxPlan = SavingGoal | SpendingBudget;
export type BoxPlanRevisionPreview = SavingGoalRevisionPreview | SpendingBudgetRevisionPreview;

export const ACTIVE_PLAN_STATUSES = new Set<BoxPlanStatus>([
	'ACTIVE',
	'READY_TO_COMPLETE',
	'OVERDUE',
]);

export function isActivePlan(plan: Pick<BoxPlanSummary, 'status'>): boolean {
	return ACTIVE_PLAN_STATUSES.has(plan.status);
}
