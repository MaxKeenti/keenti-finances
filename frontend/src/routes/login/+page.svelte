<script lang="ts">
	import { superForm } from 'sveltekit-superforms';
	import { zod4Client } from 'sveltekit-superforms/adapters';
	import { z } from 'zod';
	import * as Card from '$lib/components/ui/card';
	import * as Form from '$lib/components/ui/form';
	import * as Alert from '$lib/components/ui/alert';
	import { Input } from '$lib/components/ui/input';
	import { Button } from '$lib/components/ui/button';
	import type { PageData } from './$types';

	const loginSchema = z.object({
		username: z.string().min(1, 'Username is required'),
		password: z.string().min(1, 'Password is required'),
	});

	let { data }: { data: PageData } = $props();

	const sf = superForm(data.form, {
		validators: zod4Client(loginSchema),
	});
	const { form, errors, enhance, submitting, message } = sf;
</script>

<div class="flex min-h-svh items-center justify-center p-4">
	<Card.Root class="w-full max-w-sm">
		<Card.Header>
			<Card.Title>Sign in</Card.Title>
			<Card.Description>Enter your credentials to access Keenti Finances.</Card.Description>
		</Card.Header>
		<Card.Content>
			{#if $message}
				<Alert.Root variant="destructive" class="mb-4">
					<Alert.Description>{$message}</Alert.Description>
				</Alert.Root>
			{/if}
			<form method="POST" use:enhance class="grid gap-4">
				<Form.Field form={sf} name="username">
					<Form.Control>
						{#snippet children({ props })}
							<Form.Label>Username</Form.Label>
							<Input {...props} bind:value={$form.username} autocomplete="username" />
						{/snippet}
					</Form.Control>
					<Form.FieldErrors />
				</Form.Field>
				<Form.Field form={sf} name="password">
					<Form.Control>
						{#snippet children({ props })}
							<Form.Label>Password</Form.Label>
							<Input {...props} type="password" bind:value={$form.password} autocomplete="current-password" />
						{/snippet}
					</Form.Control>
					<Form.FieldErrors />
				</Form.Field>
				<Button type="submit" class="w-full" disabled={$submitting}>
					{$submitting ? 'Signing in…' : 'Sign in'}
				</Button>
			</form>
		</Card.Content>
	</Card.Root>
</div>
