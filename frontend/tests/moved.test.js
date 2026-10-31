// @ts-nocheck
/**
 * Where old addresses go once the app has moved (ADR-0025). The record ids below were worked out
 * by the import that carried the records across, so a change on either side fails here.
 */

import { describe, expect, test } from 'bun:test';
import { movedAddress } from '../src/lib/server/moved';

const THERE = 'https://there.example';
const RECORDS = {"account4":"74a8e819-f0f9-5d09-94f8-d78c97d8dcc6","box3":"becf486f-85c6-5eba-a2ff-24f1a9eb8842","debt1":"391a3d64-bd5d-5e56-8207-efbd3a640954","subscription7":"236c904c-9f2e-5f2b-b06e-f36a38500468"};

const moved = (address) => movedAddress(THERE, new URL(address, 'https://here.example'));

describe('old addresses', () => {
	test('a page of one record goes to that record, under the id it has there', () => {
		expect(moved('/accounts/4')).toBe(`${THERE}/accounts/${RECORDS.account4}`);
		expect(moved('/boxes/3')).toBe(`${THERE}/boxes/${RECORDS.box3}`);
		expect(moved('/debts/1')).toBe(`${THERE}/debts/${RECORDS.debt1}`);
		expect(moved('/subscriptions/7')).toBe(`${THERE}/subscriptions/${RECORDS.subscription7}`);
	});

	test('a list goes to the same list', () => {
		for (const path of [
			'/',
			'/accounts',
			'/boxes',
			'/categories',
			'/contacts',
			'/debts',
			'/login',
			'/settings',
			'/subscriptions',
			'/transactions',
			'/trash',
		]) {
			expect(moved(path)).toBe(`${THERE}${path}`);
		}
	});

	test('a public Subscription link keeps its token', () => {
		const token = '9b1f6a4e-2c3d-4e5f-8a7b-0c1d2e3f4a5b';
		expect(moved(`/public/subscription/${token}`)).toBe(`${THERE}/public/subscription/${token}`);
	});

	test('an address with no page of its own there goes to the nearest', () => {
		expect(moved('/transactions/123')).toBe(`${THERE}/transactions`);
		expect(moved('/planning')).toBe(`${THERE}/`);
		expect(moved('/callback?code=abc')).toBe(`${THERE}/`);
		expect(moved('/logout')).toBe(`${THERE}/`);
	});

	test('keeps only what a page there reads the same way', () => {
		expect(moved('/?year=2025&currency=EUR&other=1')).toBe(`${THERE}/?year=2025&currency=EUR`);
		expect(moved('/debts?direction=EGRESS&counterpart=9')).toBe(`${THERE}/debts?direction=EGRESS`);
		expect(moved('/transactions?page=2&sortBy=amount')).toBe(`${THERE}/transactions`);
		expect(moved('/accounts?payCard=4&amount=100')).toBe(`${THERE}/accounts`);
	});

	test('leaves an address it does not know as it is, for there to answer', () => {
		expect(moved('/api/accounts/4')).toBe(`${THERE}/api/accounts/4`);
		expect(moved('/accounts/not-a-number')).toBe(`${THERE}/accounts/not-a-number`);
	});

	test('never leaves the origin it was given', () => {
		expect(moved('//evil.example/path')).toStartWith(`${THERE}/`);
		expect(movedAddress(`${THERE}/`, new URL('https://here.example//evil.example'))).toBe(
			`${THERE}//evil.example`
		);
	});
});
