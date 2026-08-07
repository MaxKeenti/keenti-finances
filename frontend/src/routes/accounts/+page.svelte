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
									<summary class="cursor-pointer font-medium">Estado de cuenta inicial y calendario MSI (opcional)</summary>
									<div class="mt-3 space-y-4">
										<div class="grid gap-2 sm:grid-cols-3"><div class="grid gap-1"><label class="text-xs font-medium" for={`setup-credit-limit-${index}`}>Límite de crédito</label><Input id={`setup-credit-limit-${index}`} type="number" step="0.01" min="0.01" bind:value={account.creditLimit} placeholder="$0.00" /></div><div class="grid gap-1"><label class="text-xs font-medium" for={`setup-closing-day-${index}`}>Día habitual de corte</label><Input id={`setup-closing-day-${index}`} type="number" min="1" max="31" bind:value={account.statementClosingDay} placeholder="p. ej., 8" /></div><div class="grid gap-1"><label class="text-xs font-medium" for={`setup-due-day-${index}`}>Día habitual de pago</label><Input id={`setup-due-day-${index}`} type="number" min="1" max="31" bind:value={account.paymentDueDay} placeholder="p. ej., 28" /></div></div>
										<div class="space-y-2"><div class="flex items-center justify-between"><p class="font-medium">Estados de cuenta iniciales</p><Button type="button" size="sm" variant="outline" onclick={() => account.openingStatements = [...account.openingStatements, { periodStart: '', periodEnd: '', dueDate: '', officialBalance: '', officialMinimumPayment: '', officialAvoidInterest: '', officialNote: '' }]}>Agregar estado de cuenta</Button></div>{#each account.openingStatements as statement, statementIndex}<div class="grid gap-2 sm:grid-cols-4"><div class="grid gap-1"><label class="text-xs font-medium" for={`opening-start-${index}-${statementIndex}`}>Inicio del periodo</label><Input id={`opening-start-${index}-${statementIndex}`} type="date" bind:value={statement.periodStart} /></div><div class="grid gap-1"><label class="text-xs font-medium" for={`opening-end-${index}-${statementIndex}`}>Fecha de corte</label><Input id={`opening-end-${index}-${statementIndex}`} type="date" bind:value={statement.periodEnd} /></div><div class="grid gap-1"><label class="text-xs font-medium" for={`opening-due-${index}-${statementIndex}`}>Fecha límite de pago</label><Input id={`opening-due-${index}-${statementIndex}`} type="date" bind:value={statement.dueDate} /></div><div class="grid gap-1"><label class="text-xs font-medium" for={`opening-balance-${index}-${statementIndex}`}>Saldo del estado de cuenta</label><Input id={`opening-balance-${index}-${statementIndex}`} type="number" step="0.01" min="0" bind:value={statement.officialBalance} placeholder="$0.00" /></div><div class="grid gap-1"><label class="text-xs font-medium" for={`opening-minimum-${index}-${statementIndex}`}>Pago mínimo</label><Input id={`opening-minimum-${index}-${statementIndex}`} type="number" step="0.01" min="0" bind:value={statement.officialMinimumPayment} placeholder="$0.00" /></div><div class="grid gap-1"><label class="text-xs font-medium" for={`opening-avoid-interest-${index}-${statementIndex}`}>Pago para no generar intereses</label><Input id={`opening-avoid-interest-${index}-${statementIndex}`} type="number" step="0.01" min="0" bind:value={statement.officialAvoidInterest} placeholder="$0.00" /></div><div class="grid gap-1"><label class="text-xs font-medium" for={`opening-note-${index}-${statementIndex}`}>Nota</label><Input id={`opening-note-${index}-${statementIndex}`} bind:value={statement.officialNote} placeholder="Opcional" /></div></div>{/each}</div>
										<div class="space-y-2"><div class="flex items-center justify-between"><p class="font-medium">Calendarios MSI restantes</p><Button type="button" size="sm" variant="outline" onclick={() => account.openingMsiPlans = [...account.openingMsiPlans, { remainingAmount: '', remainingInstallmentCount: '', firstInstallmentDate: '' }]}>Agregar MSI</Button></div>{#each account.openingMsiPlans as plan, planIndex}<div class="grid gap-2 sm:grid-cols-3"><div class="grid gap-1"><label class="text-xs font-medium" for={`msi-total-${index}-${planIndex}`}>Total pendiente</label><Input id={`msi-total-${index}-${planIndex}`} type="number" step="0.01" min="0.01" bind:value={plan.remainingAmount} placeholder="$0.00" /></div><div class="grid gap-1"><label class="text-xs font-medium" for={`msi-installments-${index}-${planIndex}`}>Mensualidades restantes</label><Input id={`msi-installments-${index}-${planIndex}`} type="number" min="1" max="60" bind:value={plan.remainingInstallmentCount} placeholder="p. ej., 12" /></div><div class="grid gap-1"><label class="text-xs font-medium" for={`msi-first-date-${index}-${planIndex}`}>Primera mensualidad pendiente</label><Input id={`msi-first-date-${index}-${planIndex}`} type="date" bind:value={plan.firstInstallmentDate} /></div></div>{/each}</div>
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
				<Card.Header><Card.Title>Pago de tarjeta {account.name}</Card.Title><Card.Description>Confirma aquí los estados de cuenta bancarios. Las transferencias a esta cuenta son pagos y no afectan el saldo neto.</Card.Description></Card.Header>
				<Card.Content class="space-y-5">
					<details>
						<summary class="cursor-pointer text-sm font-medium">Calendario de la tarjeta</summary>
						<p class="mt-2 text-sm text-muted-foreground">Indica los días habituales de corte y pago de la tarjeta. Describen tu calendario regular; confirma cada estado de cuenta abajo con las fechas exactas impresas por el banco.</p>
						<form method="POST" action="?/saveCreditSettings" use:enhance class="mt-3 grid gap-3 sm:grid-cols-4">
							<input type="hidden" name="accountId" value={account.id} />
							<div class="grid gap-1"><label class="text-xs font-medium" for={`credit-limit-${account.id}`}>Límite de crédito</label><Input id={`credit-limit-${account.id}`} name="creditLimit" type="number" min="0.01" step="0.01" required value={detail?.settings?.creditLimit ?? ''} placeholder="$0.00" /></div>
							<div class="grid gap-1"><label class="text-xs font-medium" for={`closing-day-${account.id}`}>Día habitual de corte</label><Input id={`closing-day-${account.id}`} name="statementClosingDay" type="number" min="1" max="31" required value={detail?.settings?.statementClosingDay ?? ''} placeholder="p. ej., 8" /></div>
							<div class="grid gap-1"><label class="text-xs font-medium" for={`due-day-${account.id}`}>Día habitual de pago</label><Input id={`due-day-${account.id}`} name="paymentDueDay" type="number" min="1" max="31" required value={detail?.settings?.paymentDueDay ?? ''} placeholder="p. ej., 28" /></div>
							<Button type="submit">Guardar configuración</Button>
						</form>
					</details>

					<div class="space-y-1"><p class="text-sm font-medium">Confirmar un estado de cuenta</p><p class="text-sm text-muted-foreground">Captura las fechas e importes exactos que aparecen en este estado de cuenta. Pueden diferir del calendario habitual de arriba.</p></div>
					<form method="POST" action="?/confirmCreditStatement" use:enhance class="grid gap-3 sm:grid-cols-3">
						<input type="hidden" name="accountId" value={account.id} />
						<div class="grid gap-1"><label class="text-xs font-medium" for={`statement-start-${account.id}`}>Inicio del periodo</label><Input id={`statement-start-${account.id}`} name="periodStart" type="date" required /></div>
						<div class="grid gap-1"><label class="text-xs font-medium" for={`statement-end-${account.id}`}>Fecha de corte</label><Input id={`statement-end-${account.id}`} name="periodEnd" type="date" required /></div>
						<div class="grid gap-1"><label class="text-xs font-medium" for={`statement-due-${account.id}`}>Fecha límite de pago</label><Input id={`statement-due-${account.id}`} name="dueDate" type="date" required /></div>
						<div class="grid gap-1"><label class="text-xs font-medium" for={`official-balance-${account.id}`}>Saldo del estado de cuenta</label><Input id={`official-balance-${account.id}`} name="officialBalance" type="number" min="0" step="0.01" required placeholder="$0.00" /></div>
						<div class="grid gap-1"><label class="text-xs font-medium" for={`minimum-payment-${account.id}`}>Pago mínimo</label><Input id={`minimum-payment-${account.id}`} name="officialMinimumPayment" type="number" min="0" step="0.01" required placeholder="$0.00" /></div>
						<div class="grid gap-1"><label class="text-xs font-medium" for={`avoid-interest-${account.id}`}>Pago para no generar intereses</label><Input id={`avoid-interest-${account.id}`} name="officialAvoidInterest" type="number" min="0" step="0.01" required placeholder="$0.00" /></div>
						<div class="grid gap-1 sm:col-span-2"><label class="text-xs font-medium" for={`statement-note-${account.id}`}>Nota</label><Input id={`statement-note-${account.id}`} name="officialNote" placeholder="Opcional" /></div>
						<Button type="submit">Confirmar estado de cuenta</Button>
					</form>

					{#if detail?.statements?.length}
						<div class="space-y-2 text-sm">
							<p class="font-medium">Estados de cuenta confirmados</p>
							{#each detail.statements as statement}
								<div class="space-y-3 rounded-md border p-3">
									<div class="flex flex-wrap justify-between gap-2"><span>Vence: {statement.dueDate}</span><span>{fmt.format(statement.outstandingBalance)} pendiente · {fmt.format(statement.officialAvoidInterest)} para no generar intereses</span></div>
									{#if statement.reconciliationMismatch}<Alert.Root><Alert.Description>La actividad cambió después de la confirmación: hay una diferencia de {fmt.format(Math.abs(statement.mismatchAmount))} respecto al estado de cuenta. Vuelve a confirmar solo después de revisarlo con el banco.</Alert.Description></Alert.Root>{/if}
									<details>
										<summary class="cursor-pointer text-sm font-medium">Volver a confirmar cifras oficiales</summary>
										<form method="POST" action="?/reconfirmCreditStatement" use:enhance class="mt-3 grid gap-3 sm:grid-cols-3">
											<input type="hidden" name="accountId" value={account.id} /><input type="hidden" name="statementId" value={statement.id} /><input type="hidden" name="periodStart" value={statement.periodStart} /><input type="hidden" name="periodEnd" value={statement.periodEnd} />
											<div class="grid gap-1"><label class="text-xs font-medium" for={`reconfirm-due-${statement.id}`}>Fecha límite de pago</label><input id={`reconfirm-due-${statement.id}`} class="border-input bg-background h-9 rounded-md border px-3 text-sm" name="dueDate" type="date" value={statement.dueDate} /></div>
											<div class="grid gap-1"><label class="text-xs font-medium" for={`reconfirm-balance-${statement.id}`}>Saldo del estado de cuenta</label><Input id={`reconfirm-balance-${statement.id}`} name="officialBalance" type="number" min="0" step="0.01" value={statement.officialBalance} /></div>
											<div class="grid gap-1"><label class="text-xs font-medium" for={`reconfirm-minimum-${statement.id}`}>Pago mínimo</label><Input id={`reconfirm-minimum-${statement.id}`} name="officialMinimumPayment" type="number" min="0" step="0.01" value={statement.officialMinimumPayment} /></div>
											<div class="grid gap-1"><label class="text-xs font-medium" for={`reconfirm-avoid-interest-${statement.id}`}>Pago para no generar intereses</label><Input id={`reconfirm-avoid-interest-${statement.id}`} name="officialAvoidInterest" type="number" min="0" step="0.01" value={statement.officialAvoidInterest} /></div>
											<div class="grid gap-1"><label class="text-xs font-medium" for={`reconfirm-note-${statement.id}`}>Nota</label><Input id={`reconfirm-note-${statement.id}`} name="officialNote" value={statement.officialNote ?? ''} placeholder="Opcional" /></div>
											<Button type="submit">Volver a confirmar</Button>
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
