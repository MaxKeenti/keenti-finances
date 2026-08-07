<script lang="ts">
	import { enhance } from '$app/forms';
	import { Plus, ArrowLeftRight } from '@lucide/svelte';
	import * as Card from '$lib/components/ui/card';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import { Input } from '$lib/components/ui/input';
	import { NativeDatePicker } from '$lib/components/native-date-picker';
	import { mxnFormatter } from '$lib/formatting';
	import type { PageData } from './$types';

	let { data }: { data: PageData } = $props();
	type Account = { id: number; name: string; kind: string; balance: number };
	type Transfer = { id: number; sourceAccountId: number; sourceAccountName: string | null; destinationAccountId: number; destinationAccountName: string | null; amount: number; transferDate: string; notes: string | null };
	type CreditDetail = { settings: { creditLimit: number; statementClosingDay: number; paymentDueDay: number } | null; statements: Array<{ id: number; periodStart: string; periodEnd: string; dueDate: string; officialBalance: number; officialMinimumPayment: number; officialAvoidInterest: number; officialNote: string | null; paidAmount: number; outstandingBalance: number; reconciliationMismatch: boolean; mismatchAmount: number }> };
	const accounts = $derived(data.accounts as Account[]);
	const archivedAccounts = $derived(data.archivedAccounts as Account[]);
	const transfers = $derived(data.transfers as Transfer[]);
	const creditDetails = $derived(data.creditDetails as Record<number, CreditDetail>);
	const newSetupAccount = () => ({
		name: '', kind: 'DEBIT', openingBalance: 0,
		creditLimit: '', statementClosingDay: '', paymentDueDay: '',
		openingStatements: [] as Array<{ periodStart: string; periodEnd: string; dueDate: string; officialBalance: string; officialMinimumPayment: string; officialAvoidInterest: string; officialNote: string }>,
		openingMsiPlans: [] as Array<{ remainingAmount: string; remainingInstallmentCount: string; firstInstallmentDate: string }>,
	});
	let setupAccounts = $state([newSetupAccount()]);
	let addAccountOpen = $state(false);
	let addAccountError = $state('');
	const fmt = $derived(mxnFormatter(data.preferences.locale));
	const today = new Date().toISOString().slice(0, 10);
	let transferDate = $state(today);
	let transferDates = $state<Record<number, string>>({});
	const kinds = [
		{ value: 'DEBIT', label: 'Debit' }, { value: 'CHECKING', label: 'Checking' },
		{ value: 'SAVINGS', label: 'Savings' }, { value: 'CASH', label: 'Cash' }, { value: 'CREDIT', label: 'Credit' },
	];
	const kindLabel = (kind: string) => kinds.find((item) => item.value === kind)?.label ?? kind;
	const setupTotal = $derived(setupAccounts.reduce((sum, account) => sum + Number(account.openingBalance || 0), 0));
	const activationAccounts = $derived(setupAccounts.map((account) => ({
		name: account.name,
		kind: account.kind,
		openingBalance: Number(account.openingBalance || 0),
		creditSettings: account.kind === 'CREDIT' && Number(account.creditLimit) > 0
			? { creditLimit: Number(account.creditLimit), statementClosingDay: Number(account.statementClosingDay), paymentDueDay: Number(account.paymentDueDay) }
			: null,
		openingCreditStatements: account.openingStatements.filter((statement) => statement.periodStart && statement.periodEnd && statement.dueDate && statement.officialBalance !== '' && statement.officialMinimumPayment !== '' && statement.officialAvoidInterest !== '').map((statement) => ({ ...statement, officialBalance: Number(statement.officialBalance), officialMinimumPayment: Number(statement.officialMinimumPayment), officialAvoidInterest: Number(statement.officialAvoidInterest), officialNote: statement.officialNote || null })),
		openingMsiPlans: account.openingMsiPlans.filter((plan) => plan.remainingAmount !== '' && plan.remainingInstallmentCount !== '' && plan.firstInstallmentDate).map((plan) => ({ remainingAmount: Number(plan.remainingAmount), remainingInstallmentCount: Number(plan.remainingInstallmentCount), firstInstallmentDate: plan.firstInstallmentDate })),
	})));

	function openAddAccount() {
		addAccountError = '';
		addAccountOpen = true;
	}

	function enhanceCreateAccount({ formElement }: { formElement: HTMLFormElement }) {
		return async ({ result, update }: { result: { type: string; data?: { message?: string } }; update: () => Promise<void> }) => {
			if (result.type === 'success') {
				addAccountOpen = false;
				addAccountError = '';
				formElement.reset();
			} else if (result.type === 'failure') {
				addAccountError = result.data?.message ?? 'The account could not be created.';
			}
			await update();
		};
	}
</script>

<div class="space-y-6">
	<div class="flex flex-wrap items-start justify-between gap-3">
		<div>
		<h1 class="text-2xl font-semibold tracking-tight">Accounts</h1>
		<p class="text-sm text-muted-foreground">Track real money and credit debt without changing Net Balance when you transfer between accounts.</p>
		</div>
		{#if data.status.active}
			<Button onclick={openAddAccount}><Plus /> Add account</Button>
		{/if}
	</div>

	{#if !data.status.active}
		<Card.Root>
			<Card.Header><Card.Title>Set up account tracking</Card.Title><Card.Description>Your opening balances must equal the Net Balance already recorded in Keenti: {fmt.format(data.status.transactionNetBalance)}.</Card.Description></Card.Header>
			<Card.Content>
				<form method="POST" action="?/activate" use:enhance class="space-y-4">
					<input type="hidden" name="activationDate" value={today} />
						<input type="hidden" name="accounts" value={JSON.stringify(activationAccounts)} />
					{#each setupAccounts as account, index}
							<div class="grid gap-3 sm:grid-cols-3">
							<Input bind:value={account.name} placeholder="Account name, e.g. BBVA" />
							<select class="border-input bg-background h-9 rounded-md border px-3 text-sm" bind:value={account.kind}>{#each kinds as kind}<option value={kind.value}>{kind.label}</option>{/each}</select>
							<Input type="number" step="0.01" bind:value={account.openingBalance} placeholder="Opening balance" />
							</div>
							{#if account.kind === 'CREDIT'}
								<details class="rounded-md border p-3 text-sm">
									<summary class="cursor-pointer font-medium">Opening Credit Statement and MSI schedule (optional)</summary>
									<div class="mt-3 space-y-4">
										<div class="grid gap-2 sm:grid-cols-3"><Input type="number" step="0.01" min="0.01" bind:value={account.creditLimit} placeholder="Credit limit" /><Input type="number" min="1" max="31" bind:value={account.statementClosingDay} placeholder="Closing day" /><Input type="number" min="1" max="31" bind:value={account.paymentDueDay} placeholder="Due day" /></div>
										<div class="space-y-2"><div class="flex items-center justify-between"><p class="font-medium">Opening statements</p><Button type="button" size="sm" variant="outline" onclick={() => account.openingStatements = [...account.openingStatements, { periodStart: '', periodEnd: '', dueDate: '', officialBalance: '', officialMinimumPayment: '', officialAvoidInterest: '', officialNote: '' }]}>Add statement</Button></div>{#each account.openingStatements as statement}<div class="grid gap-2 sm:grid-cols-4"><Input type="date" bind:value={statement.periodStart} aria-label="Statement start" /><Input type="date" bind:value={statement.periodEnd} aria-label="Statement close" /><Input type="date" bind:value={statement.dueDate} aria-label="Statement due" /><Input type="number" step="0.01" min="0" bind:value={statement.officialBalance} placeholder="Statement balance" /><Input type="number" step="0.01" min="0" bind:value={statement.officialMinimumPayment} placeholder="Minimum payment" /><Input type="number" step="0.01" min="0" bind:value={statement.officialAvoidInterest} placeholder="Avoid interest" /><Input bind:value={statement.officialNote} placeholder="Note" /></div>{/each}</div>
										<div class="space-y-2"><div class="flex items-center justify-between"><p class="font-medium">Remaining MSI schedules</p><Button type="button" size="sm" variant="outline" onclick={() => account.openingMsiPlans = [...account.openingMsiPlans, { remainingAmount: '', remainingInstallmentCount: '', firstInstallmentDate: '' }]}>Add MSI</Button></div>{#each account.openingMsiPlans as plan}<div class="grid gap-2 sm:grid-cols-3"><Input type="number" step="0.01" min="0.01" bind:value={plan.remainingAmount} placeholder="Remaining total" /><Input type="number" min="1" max="60" bind:value={plan.remainingInstallmentCount} placeholder="Installments left" /><Input type="date" bind:value={plan.firstInstallmentDate} aria-label="First remaining installment" /></div>{/each}</div>
									</div>
								</details>
							{/if}
						{/each}
						<div class="flex flex-wrap items-center gap-3"><Button type="button" variant="outline" onclick={() => setupAccounts = [...setupAccounts, newSetupAccount()]}><Plus /> Add account</Button><span class="text-sm text-muted-foreground">Entered: {fmt.format(setupTotal)}</span><Button type="submit">Activate tracking</Button></div>
				</form>
			</Card.Content>
		</Card.Root>
	{:else}
		<section class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
			{#each accounts as account}
				<a class="rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" href={`/accounts/${account.id}`}><Card.Root class="h-full transition-colors hover:bg-muted/40"><Card.Header><Card.Description>{kindLabel(account.kind)}</Card.Description><Card.Title>{account.name}</Card.Title><p class:text-destructive={account.kind === 'CREDIT' && account.balance < 0} class="text-xl tabular-nums">{account.kind === 'CREDIT' && account.balance < 0 ? `${fmt.format(Math.abs(account.balance))} owed` : fmt.format(account.balance)}</p></Card.Header></Card.Root></a>
			{/each}
		</section>

		<Card.Root>
			<Card.Header><Card.Title>Transferencia</Card.Title><Card.Description>Mueve dinero entre cuentas sin afectar el saldo neto ni las Cajas.</Card.Description></Card.Header>
			<Card.Content><form method="POST" action="?/transfer" use:enhance class="grid gap-3 sm:grid-cols-6">
				<div class="grid gap-1"><label class="text-xs font-medium" for="transfer-source">Desde</label><select id="transfer-source" class="border-input bg-background h-9 rounded-md border px-3 text-sm" name="sourceAccountId" required><option value="">Selecciona una cuenta</option>{#each accounts as account}<option value={account.id}>{account.name}</option>{/each}</select></div>
				<div class="grid gap-1"><label class="text-xs font-medium" for="transfer-destination">Hacia</label><select id="transfer-destination" class="border-input bg-background h-9 rounded-md border px-3 text-sm" name="destinationAccountId" required><option value="">Selecciona una cuenta</option>{#each accounts as account}<option value={account.id}>{account.name}</option>{/each}</select></div>
				<div class="grid gap-1"><label class="text-xs font-medium" for="transfer-amount">Monto</label><Input id="transfer-amount" name="amount" type="number" step="0.01" min="0.01" required placeholder="$0.00" /></div>
				<div class="grid gap-1"><span class="text-xs font-medium">Fecha</span><NativeDatePicker name="transferDate" value={transferDate} onValueChange={(value) => transferDate = value} aria-label="Fecha de transferencia" /></div>
				<div class="grid gap-1"><label class="text-xs font-medium" for="transfer-notes">Nota</label><Input id="transfer-notes" name="notes" placeholder="Opcional" /></div>
				<Button class="self-end" type="submit"><ArrowLeftRight /> Transferir</Button>
			</form></Card.Content>
		</Card.Root>

		<Card.Root>
			<Card.Header><Card.Title>Historial de transferencias</Card.Title><Card.Description>Las transferencias son neutrales y no afectan el saldo neto.</Card.Description></Card.Header>
			<Card.Content>
				{#if transfers.length === 0}
					<p class="text-sm text-muted-foreground">No Transfers recorded yet.</p>
				{:else}
					<div class="divide-y rounded-md border">
						{#each transfers as transfer}
							<details class="p-3 text-sm">
								<summary class="flex cursor-pointer list-none flex-wrap items-center justify-between gap-2"><div><p class="font-medium">{transfer.sourceAccountName ?? 'Archived account'} → {transfer.destinationAccountName ?? 'Archived account'}</p><p class="text-muted-foreground">{transfer.transferDate}{transfer.notes ? ` · ${transfer.notes}` : ''}</p></div><span class="tabular-nums">{fmt.format(transfer.amount)}</span></summary>
								<div class="mt-3 space-y-3 border-t pt-3">
									<form method="POST" action="?/updateTransfer" use:enhance class="grid gap-3 sm:grid-cols-3">
										<input type="hidden" name="id" value={transfer.id} />
										<select class="border-input bg-background h-9 rounded-md border px-3 text-sm" name="sourceAccountId" value={transfer.sourceAccountId}>{#each accounts as account}<option value={account.id}>{account.name}</option>{/each}</select>
										<select class="border-input bg-background h-9 rounded-md border px-3 text-sm" name="destinationAccountId" value={transfer.destinationAccountId}>{#each accounts as account}<option value={account.id}>{account.name}</option>{/each}</select>
										<Input name="amount" type="number" step="0.01" min="0.01" value={transfer.amount} />
										<NativeDatePicker name="transferDate" value={transferDates[transfer.id] ?? transfer.transferDate} onValueChange={(value) => transferDates = { ...transferDates, [transfer.id]: value }} aria-label="Transfer date" />
										<Input name="notes" value={transfer.notes ?? ''} placeholder="Note (optional)" />
										<div class="flex gap-2"><Button type="submit" size="sm">Save changes</Button></div>
									</form>
									<form method="POST" action="?/deleteTransfer" use:enhance><input type="hidden" name="id" value={transfer.id} /><Button type="submit" size="sm" variant="destructive">Delete transfer</Button></form>
								</div>
							</details>
						{/each}
					</div>
				{/if}
			</Card.Content>
		</Card.Root>

		{#each accounts.filter((account) => account.kind === 'CREDIT') as account}
			{@const detail = creditDetails[account.id]}
			<Card.Root>
				<Card.Header><Card.Title>{account.name} credit payment</Card.Title><Card.Description>Confirm bank statements here. Transfers into this account remain payments and never affect Net Balance.</Card.Description></Card.Header>
				<Card.Content class="space-y-5">
					<details>
						<summary class="cursor-pointer text-sm font-medium">Credit account settings</summary>
						<form method="POST" action="?/saveCreditSettings" use:enhance class="mt-3 grid gap-3 sm:grid-cols-4">
							<input type="hidden" name="accountId" value={account.id} />
							<Input name="creditLimit" type="number" min="0.01" step="0.01" required value={detail?.settings?.creditLimit ?? ''} placeholder="Credit limit" />
							<Input name="statementClosingDay" type="number" min="1" max="31" required value={detail?.settings?.statementClosingDay ?? ''} placeholder="Closing day" />
							<Input name="paymentDueDay" type="number" min="1" max="31" required value={detail?.settings?.paymentDueDay ?? ''} placeholder="Due day" />
							<Button type="submit">Save settings</Button>
						</form>
					</details>

					<form method="POST" action="?/confirmCreditStatement" use:enhance class="grid gap-3 sm:grid-cols-3">
						<input type="hidden" name="accountId" value={account.id} />
						<Input name="periodStart" type="date" required aria-label="Statement period start" />
						<Input name="periodEnd" type="date" required aria-label="Statement period end" />
						<Input name="dueDate" type="date" required aria-label="Statement due date" />
						<Input name="officialBalance" type="number" min="0" step="0.01" required placeholder="Official balance" />
						<Input name="officialMinimumPayment" type="number" min="0" step="0.01" required placeholder="Minimum payment" />
						<Input name="officialAvoidInterest" type="number" min="0" step="0.01" required placeholder="Avoid-interest payment" />
						<Input name="officialNote" class="sm:col-span-2" placeholder="Optional note" />
						<Button type="submit">Confirm statement</Button>
					</form>

					{#if detail?.statements?.length}
						<div class="space-y-2 text-sm">
							<p class="font-medium">Confirmed statements</p>
							{#each detail.statements as statement}
								<div class="space-y-3 rounded-md border p-3">
									<div class="flex flex-wrap justify-between gap-2"><span>Due {statement.dueDate}</span><span>{fmt.format(statement.outstandingBalance)} remaining · {fmt.format(statement.officialAvoidInterest)} to avoid interest</span></div>
									{#if statement.reconciliationMismatch}<Alert.Root><Alert.Description>Activity changed after confirmation: {fmt.format(Math.abs(statement.mismatchAmount))} differs from the official statement. Reconfirm only after checking the bank statement.</Alert.Description></Alert.Root>{/if}
									<details>
										<summary class="cursor-pointer text-sm font-medium">Reconfirm official figures</summary>
										<form method="POST" action="?/reconfirmCreditStatement" use:enhance class="mt-3 grid gap-3 sm:grid-cols-3">
											<input type="hidden" name="accountId" value={account.id} /><input type="hidden" name="statementId" value={statement.id} /><input type="hidden" name="periodStart" value={statement.periodStart} /><input type="hidden" name="periodEnd" value={statement.periodEnd} />
											<input class="border-input bg-background h-9 rounded-md border px-3 text-sm" name="dueDate" type="date" value={statement.dueDate} aria-label="Statement due date" />
											<Input name="officialBalance" type="number" min="0" step="0.01" value={statement.officialBalance} />
											<Input name="officialMinimumPayment" type="number" min="0" step="0.01" value={statement.officialMinimumPayment} />
											<Input name="officialAvoidInterest" type="number" min="0" step="0.01" value={statement.officialAvoidInterest} />
											<Input name="officialNote" value={statement.officialNote ?? ''} placeholder="Optional note" />
											<Button type="submit">Reconfirm statement</Button>
										</form>
									</details>
								</div>
							{/each}
						</div>
					{/if}
				</Card.Content>
			</Card.Root>
		{/each}

		{#if archivedAccounts.length}
			<section class="space-y-3">
				<div><h2 class="text-lg font-semibold">Archived accounts</h2><p class="text-sm text-muted-foreground">Historical activity is preserved. Restore an account before using it again.</p></div>
				<div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
					{#each archivedAccounts as account}
						<a class="rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" href={`/accounts/${account.id}`}><Card.Root class="h-full opacity-75 transition-colors hover:bg-muted/40"><Card.Header><Card.Description>{kindLabel(account.kind)} · Archived</Card.Description><Card.Title>{account.name}</Card.Title><p class="text-xl tabular-nums">{fmt.format(account.balance)}</p></Card.Header></Card.Root></a>
					{/each}
				</div>
			</section>
		{/if}
	{/if}
</div>

{#if data.status.active}
	<Dialog.Root bind:open={addAccountOpen}>
		<Dialog.Content class="sm:max-w-md">
			<Dialog.Header>
				<Dialog.Title>Add account</Dialog.Title>
				<Dialog.Description>Use zero for a new Account. A non-zero opening balance deliberately introduces money or debt that was not tracked before.</Dialog.Description>
			</Dialog.Header>

			{#if addAccountError}
				<Alert.Root variant="destructive"><Alert.Description>{addAccountError}</Alert.Description></Alert.Root>
			{/if}

			<form method="POST" action="?/create" use:enhance={enhanceCreateAccount} class="grid gap-4">
				<div class="grid gap-2">
					<label class="text-sm font-medium" for="account-name">Account name</label>
					<Input id="account-name" name="name" required maxlength={100} placeholder="e.g. BBVA Savings" />
				</div>
				<div class="grid gap-2">
					<label class="text-sm font-medium" for="account-kind">Account kind</label>
					<select id="account-kind" class="border-input bg-background h-9 rounded-md border px-3 text-sm" name="kind">
						{#each kinds as kind}<option value={kind.value}>{kind.label}</option>{/each}
					</select>
				</div>
					<div class="grid gap-2">
						<label class="text-sm font-medium" for="account-opening-balance">Opening balance</label>
						<Input id="account-opening-balance" name="openingBalance" type="number" step="0.01" value="0" />
					</div>
				<div class="flex justify-end gap-2">
					<Button type="button" variant="outline" onclick={() => addAccountOpen = false}>Cancel</Button>
					<Button type="submit">Add account</Button>
				</div>
			</form>
		</Dialog.Content>
	</Dialog.Root>
{/if}
