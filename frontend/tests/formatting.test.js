// @ts-nocheck
import { describe, expect, test } from 'bun:test';
import { dateInTimeZone } from '../src/lib/formatting';

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
