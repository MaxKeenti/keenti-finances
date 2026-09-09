/**
 * Controlled clock and time-zone inputs for fixture scenarios.
 *
 * Date-boundary behavior is only reproducible when both the instant and the
 * User's time zone are explicit: `2026-09-08T04:30:00Z` is 7 September in
 * Mexico City and 8 September in Tokyo. Fixtures therefore carry a clock
 * instead of reading the ambient wall clock.
 */

export type FixtureClock = {
	/** Fixed instant, as an ISO-8601 UTC timestamp. */
	now: string;
	/** IANA time zone the User is assumed to be in. */
	timeZone: string;
};

export const MEXICO_CITY_EVENING: FixtureClock = {
	now: '2026-09-08T04:30:00.000Z',
	timeZone: 'America/Mexico_City',
};

export const TOKYO_SAME_INSTANT: FixtureClock = {
	now: '2026-09-08T04:30:00.000Z',
	timeZone: 'Asia/Tokyo',
};

export const MEXICO_CITY_MIDDAY: FixtureClock = {
	now: '2026-09-07T18:00:00.000Z',
	timeZone: 'America/Mexico_City',
};

/** The instant a scenario's clock points at, as a `Date`. */
export function instantOf(clock: FixtureClock): Date {
	return new Date(clock.now);
}

/**
 * Runs `body` with `Date.now()` and `new Date()` pinned to the clock's instant.
 *
 * The global is always restored, including when `body` throws, so one test
 * cannot leak a frozen clock into the next.
 */
export async function withFixtureClock<T>(
	clock: FixtureClock,
	body: () => T | Promise<T>,
): Promise<T> {
	const RealDate = globalThis.Date;
	const fixed = new RealDate(clock.now).getTime();

	class FixedDate extends RealDate {
		constructor(...args: ConstructorParameters<typeof Date>) {
			// @ts-expect-error - forwarding the real Date overloads verbatim
			super(...(args.length === 0 ? [fixed] : args));
		}

		static now(): number {
			return fixed;
		}
	}

	globalThis.Date = FixedDate as unknown as DateConstructor;
	try {
		return await body();
	} finally {
		globalThis.Date = RealDate;
	}
}
