export type BalanceSummary = {
	netBalance: number;
	inBoxes: number;
	availableToSpend: number;
};

export type BoxDto = {
	id: number;
	name: string;
	hue: number;
	icon: string | null;
	description: string | null;
	displayOrder: number;
	balance: number;
	archived: boolean;
	createdAt: string;
	updatedAt: string;
	version?: number;
};

export type BoxMovementType =
	| 'DEPOSIT'
	| 'WITHDRAWAL'
	| 'TRANSFER_IN'
	| 'TRANSFER_OUT'
	| 'SPENDING';

export type BoxMovementDto = {
	id: number;
	type: BoxMovementType;
	amount: number;
	effectiveDate: string;
	createdAt: string;
	runningBalance: number;
	relatedBoxId?: number | null;
	relatedBoxName?: string | null;
	relatedTransactionId?: number | null;
	relatedTransactionDescription?: string | null;
	relatedTransactionChanged: boolean;
	relatedTransactionRemoved: boolean;
};

export type BoxMovementTransactionSourceState = 'NONE' | 'CURRENT' | 'CHANGED' | 'REMOVED';

export function isCorrectableBoxMovement(type: BoxMovementType): boolean {
	return type !== 'SPENDING';
}

export function boxMovementTransactionSourceState(
	movement: Pick<
		BoxMovementDto,
		'relatedTransactionId' | 'relatedTransactionChanged' | 'relatedTransactionRemoved'
	>,
): BoxMovementTransactionSourceState {
	if (movement.relatedTransactionId == null) return 'NONE';
	if (movement.relatedTransactionRemoved) return 'REMOVED';
	if (movement.relatedTransactionChanged) return 'CHANGED';
	return 'CURRENT';
}

export function hasClickableBoxMovementTransaction(
	movement: Pick<BoxMovementDto, 'relatedTransactionId' | 'relatedTransactionRemoved'>,
): boolean {
	return movement.relatedTransactionId != null && !movement.relatedTransactionRemoved;
}

export const EMPTY_BALANCE_SUMMARY: BalanceSummary = {
	netBalance: 0,
	inBoxes: 0,
	availableToSpend: 0,
};
