// @ts-nocheck
import { expect, test } from 'bun:test';
import { load } from '../src/routes/+layout.server';
import { createFixtureBackend } from './fixtures/backend';
import { HARNESS_ROUTES } from './fixtures/harness-routes';
import { withFixtureClock, MEXICO_CITY_EVENING } from './fixtures/clock';

async function calendar(options = {}) {
 const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', options);
 backend.declareDefaultRoutes(HARNESS_ROUTES);
 const result = await load({ locals: { session: { user: { id: 'fixture' } } }, fetch: backend.fetch,
  cookies: { get: () => undefined, set: () => {} }, url: new URL('http://app.test/') });
 backend.assertNoUndeclaredRoutes();
 return result;
}

test('unavailable preferences cannot turn the appearance default into an obligation calendar', async () => {
 for (const failure of [{kind:'status', status:500}, {kind:'unreachable'}]) {
  const data = await calendar({failures: {'GET /api/user/preferences': failure}});
  expect(data.preferences.timeZone).toBe('America/Mexico_City');
  expect(data.obligationToday.status).toBe('unavailable');
  expect(data.balanceSummary.status).toBe('ok');
 }
});

test('missing and invalid configured zones keep the calendar unavailable', async () => {
 for (const timeZone of [undefined, '', 'Invalid/Zone']) {
  const data = await calendar({routes: {'GET /api/user/preferences': {...HARNESS_ROUTES['GET /api/user/preferences'],timeZone}}});
  expect(data.obligationToday.status).toBe('unavailable');
 }
});

test('the server captures the configured user day once for client hydration', async () => {
 await withFixtureClock(MEXICO_CITY_EVENING, async () => {
  const mexico = await calendar();
  const tokyo = await calendar({routes: {'GET /api/user/preferences': {...HARNESS_ROUTES['GET /api/user/preferences'],timeZone:'Asia/Tokyo'}}});
  expect(mexico.obligationToday).toEqual({status:'ok',day:'2026-09-07'});
  expect(tokyo.obligationToday).toEqual({status:'ok',day:'2026-09-08'});
 });
});
