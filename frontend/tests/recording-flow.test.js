// @ts-nocheck
import { test, expect } from 'bun:test';
import { stringify } from 'devalue';
import { load, actions } from '../src/routes/transactions/+page.server';
import { createFixtureBackend } from './fixtures/backend';
const cookies = {get:()=>undefined};
test('Box shortcut and filters load full history without writes', async () => {
 const backend = createFixtureBackend('FX-RECORDING-01');
 const data = await load({fetch:backend.fetch,cookies,url:new URL('http://app.test/transactions?expenseFromBox=9205&q=needle'),parent:async()=>({preferences:{timeZone:'America/Mexico_City',transactionPageSize:25,transactionSortBy:'transactionDate',transactionSortDirection:'desc'}})});
 expect(data.draftBoxId).toBe(9205);
 expect(data.activityTransactions).toHaveLength(31);
 expect(data.filters.q).toBe('needle');
 expect(backend.requests.every(r=>r.method==='GET')).toBe(true);
 backend.assertNoUndeclaredRoutes();
});
for (const funding of [[], [{boxId:9206,amount:60}], [{boxId:9206,amount:100}]]) {
 test(`recording expense with ${funding[0]?.amount ?? 0} funding sends one atomic Transaction request`, async () => {
  const body = new FormData();
  body.set('__superform_json',stringify({amount:100,direction:'EGRESS',description:'Groceries',transactionDate:'2026-09-07',categoryId:9801,contactId:'',accountId:9101,boxFunding:funding,boxDistributions:[]}));
  const calls=[];
  const result = await actions.create({cookies,request:new Request('http://app.test/transactions?/create',{method:'POST',body}),fetch:async(url,init)=>{calls.push({url,init});return Response.json({id:9699});}});
  expect(result.form.valid).toBe(true);
  expect(calls).toHaveLength(1);
  expect(new URL(calls[0].url).pathname).toBe('/api/transactions');
  expect(JSON.parse(calls[0].init.body).boxFunding).toEqual(funding);
 });
}
