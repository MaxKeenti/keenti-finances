<script lang="ts">
	import { enhance } from '$app/forms';
	import ChevronsUpDown from '@lucide/svelte/icons/chevrons-up-down';
	import Plus from '@lucide/svelte/icons/plus';
	import { CurrencyInput } from '$lib/components/currency-input';
	import { NativeDatePicker } from '$lib/components/native-date-picker';
	import { NativeSelect } from '$lib/components/native-select';
	import * as Card from '$lib/components/ui/card';
	import * as Collapsible from '$lib/components/ui/collapsible';
	import { Button, buttonVariants } from '$lib/components/ui/button';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';
	import { mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';

	type OpeningStatement = {
		periodStart: string;
		periodEnd: string;
		dueDate: string;
		officialBalance: string | number;
		officialMinimumPayment: string | number;
		officialAvoidInterest: string | number;
		officialNote: string;
	};
	type OpeningMsiPlan = { remainingAmount: string | number; remainingInstallmentCount: string | number; firstInstallmentDate: string };
	type SetupAccount = {
		name: string;
		kind: string;
		openingBalance: string | number;
		creditLimit: string | number;
		statementClosingDay: string | number;
		paymentDueDay: string | number;
		openingStatements: OpeningStatement[];
		openingMsiPlans: OpeningMsiPlan[];
	};

	let { transactionNetBalance, locale, today }: { transactionNetBalance: number; locale: string; today: string } = $props();
	const fmt = $derived(mxnFormatter(locale));
	const kindItems = $derived([
		{ value: 'DEBIT', label: m.account_kind_debit() },
		{ value: 'CHECKING', label: m.account_kind_checking() },
		{ value: 'SAVINGS', label: m.account_kind_savings() },
		{ value: 'CASH', label: m.account_kind_cash() },
		{ value: 'CREDIT', label: m.account_kind_credit() },
	]);

	const newAccount = (): SetupAccount => ({
		name: '', kind: 'DEBIT', openingBalance: 0,
		creditLimit: '', statementClosingDay: '', paymentDueDay: '', openingStatements: [], openingMsiPlans: [],
	});
	const newStatement = (): OpeningStatement => ({
		periodStart: '', periodEnd: '', dueDate: '', officialBalance: '', officialMinimumPayment: '', officialAvoidInterest: '', officialNote: '',
	});
	const newMsiPlan = (): OpeningMsiPlan => ({ remainingAmount: '', remainingInstallmentCount: '', firstInstallmentDate: '' });

	let accounts = $state<SetupAccount[]>([newAccount()]);
	const total = $derived(accounts.reduce((sum, account) => sum + Number(account.openingBalance || 0), 0));
	const payload = $derived(accounts.map((account) => ({
		name: account.name,
		kind: account.kind,
		openingBalance: Number(account.openingBalance || 0),
		creditSettings: account.kind === 'CREDIT' && Number(account.creditLimit) > 0
			? { creditLimit: Number(account.creditLimit), statementClosingDay: Number(account.statementClosingDay), paymentDueDay: Number(account.paymentDueDay) }
			: null,
		openingCreditStatements: account.openingStatements
			.filter((statement) => statement.periodStart && statement.periodEnd && statement.dueDate && statement.officialBalance !== '' && statement.officialMinimumPayment !== '' && statement.officialAvoidInterest !== '')
			.map((statement) => ({ ...statement, officialBalance: Number(statement.officialBalance), officialMinimumPayment: Number(statement.officialMinimumPayment), officialAvoidInterest: Number(statement.officialAvoidInterest), officialNote: statement.officialNote || null })),
		openingMsiPlans: account.openingMsiPlans
			.filter((plan) => plan.remainingAmount !== '' && plan.remainingInstallmentCount !== '' && plan.firstInstallmentDate)
			.map((plan) => ({ remainingAmount: Number(plan.remainingAmount), remainingInstallmentCount: Number(plan.remainingInstallmentCount), firstInstallmentDate: plan.firstInstallmentDate })),
	})));
</script>

<Card.Root>
	<Card.Header>
		<Card.Title>{m.accounts_setup_title()}</Card.Title>
		<Card.Description>{m.accounts_setup_description({ amount: fmt.format(transactionNetBalance) })}</Card.Description>
	</Card.Header>
	<Card.Content>
		<form method="POST" action="?/activate" use:enhance class="space-y-5">
			<input type="hidden" name="activationDate" value={today} />
			<input type="hidden" name="accounts" value={JSON.stringify(payload)} />
			{#each accounts as account, accountIndex}
				<div class="space-y-4 rounded-lg border bg-muted/20 p-4">
					<div class="grid gap-4 md:grid-cols-3">
						<div class="grid gap-2">
							<Label for={`setup-name-${accountIndex}`}>{m.account_name()}</Label>
							<Input id={`setup-name-${accountIndex}`} bind:value={account.name} placeholder={m.account_name_placeholder()} required />
						</div>
						<div class="grid gap-2">
							<Label for={`setup-kind-${accountIndex}`}>{m.account_kind()}</Label>
							<NativeSelect id={`setup-kind-${accountIndex}`} name={`setup-kind-${accountIndex}`} value={account.kind} onValueChange={(value) => (account.kind = value)} items={kindItems} />
						</div>
						<div class="grid gap-2">
							<Label for={`setup-balance-${accountIndex}`}>{m.account_opening_balance()}</Label>
							<CurrencyInput id={`setup-balance-${accountIndex}`} bind:value={account.openingBalance} {locale} required />
						</div>
					</div>
					{#if account.kind === 'CREDIT'}
						<Collapsible.Root>
							<Collapsible.Trigger class={buttonVariants({ variant: 'outline', class: 'w-full justify-between' })}>
								{m.account_credit_setup_optional()}<ChevronsUpDown />
							</Collapsible.Trigger>
							<Collapsible.Content class="pt-4">
								<div class="space-y-5 rounded-lg bg-background p-4 ring-1 ring-border">
									<div>
										<h3 class="font-medium">{m.account_credit_settings()}</h3>
										<div class="mt-3 grid gap-4 md:grid-cols-3">
											<div class="grid gap-2"><Label for={`setup-limit-${accountIndex}`}>{m.account_credit_limit()}</Label><CurrencyInput id={`setup-limit-${accountIndex}`} bind:value={account.creditLimit} {locale} /></div>
											<div class="grid gap-2"><Label for={`setup-close-${accountIndex}`}>{m.account_statement_closing_day()}</Label><Input id={`setup-close-${accountIndex}`} type="number" min="1" max="31" bind:value={account.statementClosingDay} /></div>
											<div class="grid gap-2"><Label for={`setup-due-${accountIndex}`}>{m.account_payment_due_day()}</Label><Input id={`setup-due-${accountIndex}`} type="number" min="1" max="31" bind:value={account.paymentDueDay} /></div>
										</div>
									</div>
									<div class="border-t pt-5">
										<div class="flex flex-wrap items-center justify-between gap-2"><h3 class="font-medium">{m.account_opening_statements()}</h3><Button type="button" size="sm" variant="outline" onclick={() => (account.openingStatements = [...account.openingStatements, newStatement()])}>{m.account_add_statement()}</Button></div>
										<div class="mt-3 space-y-4">
											{#each account.openingStatements as statement, statementIndex}
												<div class="grid gap-4 rounded-md bg-muted/30 p-3 md:grid-cols-3">
													<div class="grid gap-2"><Label>{m.account_period_start()}</Label><NativeDatePicker name={`opening-start-${accountIndex}-${statementIndex}`} value={statement.periodStart} onValueChange={(value) => (statement.periodStart = value)} /></div>
													<div class="grid gap-2"><Label>{m.account_period_end()}</Label><NativeDatePicker name={`opening-end-${accountIndex}-${statementIndex}`} value={statement.periodEnd} onValueChange={(value) => (statement.periodEnd = value)} /></div>
													<div class="grid gap-2"><Label>{m.account_due_date()}</Label><NativeDatePicker name={`opening-due-${accountIndex}-${statementIndex}`} value={statement.dueDate} onValueChange={(value) => (statement.dueDate = value)} /></div>
													<div class="grid gap-2"><Label>{m.account_official_balance()}</Label><CurrencyInput bind:value={statement.officialBalance} {locale} /></div>
													<div class="grid gap-2"><Label>{m.account_minimum_payment()}</Label><CurrencyInput bind:value={statement.officialMinimumPayment} {locale} /></div>
													<div class="grid gap-2"><Label>{m.account_avoid_interest()}</Label><CurrencyInput bind:value={statement.officialAvoidInterest} {locale} /></div>
													<div class="grid gap-2 md:col-span-3"><Label>{m.common_notes()} {m.common_optional()}</Label><Input bind:value={statement.officialNote} /></div>
												</div>
											{/each}
										</div>
									</div>
									<div class="border-t pt-5">
										<div class="flex flex-wrap items-center justify-between gap-2"><h3 class="font-medium">{m.account_remaining_msi()}</h3><Button type="button" size="sm" variant="outline" onclick={() => (account.openingMsiPlans = [...account.openingMsiPlans, newMsiPlan()])}>{m.account_add_msi()}</Button></div>
										<div class="mt-3 space-y-4">
											{#each account.openingMsiPlans as plan, planIndex}
												<div class="grid gap-4 rounded-md bg-muted/30 p-3 md:grid-cols-3">
													<div class="grid gap-2"><Label>{m.account_remaining_total()}</Label><CurrencyInput bind:value={plan.remainingAmount} {locale} /></div>
													<div class="grid gap-2"><Label>{m.account_installments_left()}</Label><Input type="number" min="1" max="60" bind:value={plan.remainingInstallmentCount} /></div>
													<div class="grid gap-2"><Label>{m.account_first_installment()}</Label><NativeDatePicker name={`opening-msi-${accountIndex}-${planIndex}`} value={plan.firstInstallmentDate} onValueChange={(value) => (plan.firstInstallmentDate = value)} /></div>
												</div>
											{/each}
										</div>
									</div>
								</div>
							</Collapsible.Content>
						</Collapsible.Root>
					{/if}
				</div>
			{/each}
			<div class="flex flex-wrap items-center justify-between gap-3 border-t pt-4">
				<div class="flex flex-wrap items-center gap-3"><Button type="button" variant="outline" onclick={() => (accounts = [...accounts, newAccount()])}><Plus />{m.accounts_add()}</Button><span class="text-sm text-muted-foreground">{m.accounts_entered({ amount: fmt.format(total) })}</span></div>
				<Button type="submit">{m.accounts_activate()}</Button>
			</div>
		</form>
	</Card.Content>
</Card.Root>
