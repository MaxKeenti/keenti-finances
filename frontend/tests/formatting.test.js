// @ts-nocheck
import { describe, expect, test } from 'bun:test';
import { dateInTimeZone, formatDateOnly, formatMonthYear } from '../src/lib/formatting';

describe('dateInTimeZone', () => {
	test('uses the user calendar day instead of the UTC day', () => {
		const instant = new Date('2026-08-01T04:30:00.000Z');

		expect(dateInTimeZone('America/Mexico_City', instant)).toBe('2026-07-31');
		expect(dateInTimeZone('Asia/Tokyo', instant)).toBe('2026-08-01');
	});

	test('falls back to UTC for an invalid time zone', () => {
		expect(dateInTimeZone('Not/A_Time_Zone', new Date('2026-08-01T00:00:00.000Z'))).toBe(
			'2026-08-01',
		);
	});
});

describe('formatDateOnly', () => {
	test('formats ISO dates as calendar dates in the selected locale', () => {
		expect(formatDateOnly('2026-08-05', 'es')).toBe('05 ago 2026');
		expect(formatDateOnly('2026-08-05', 'en')).toBe('Aug 05, 2026');
	});

	test('preserves a non-date value rather than shifting or corrupting it', () => {
		expect(formatDateOnly('not-a-date', 'es')).toBe('not-a-date');
	});
});

describe('formatMonthYear', () => {
	test('labels a billing period without a UTC round-trip', () => {
		expect(formatMonthYear('2026-09-01', 'es')).toBe('sep 2026');
		expect(formatMonthYear('2026-09-01', 'en')).toBe('Sep 2026');
	});

	test('returns a non-date value instead of throwing on an invalid Date', () => {
		// `Intl.DateTimeFormat.format` throws a RangeError on an invalid Date,
		// which during SSR would replace the page with an error.
		expect(() => formatMonthYear('septiembre', 'es')).not.toThrow();
		expect(formatMonthYear('septiembre', 'es')).toBe('septiembre');
	});
});
