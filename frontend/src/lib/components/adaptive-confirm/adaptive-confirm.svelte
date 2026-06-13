<script lang="ts">
	import * as AlertDialog from '$lib/components/ui/alert-dialog';
	import { adaptiveConfirmState } from './adaptive-confirm-state.svelte';

	const request = $derived(adaptiveConfirmState.pending);
</script>

<AlertDialog.Root open={request !== null} onOpenChange={(open) => !open && adaptiveConfirmState.settle(false)}>
	{#if request}
		<AlertDialog.Content size="sm">
			<AlertDialog.Header>
				<AlertDialog.Title>{request.title}</AlertDialog.Title>
				{#if request.description}
					<AlertDialog.Description>{request.description}</AlertDialog.Description>
				{/if}
			</AlertDialog.Header>
			<AlertDialog.Footer>
				<AlertDialog.Cancel onclick={() => adaptiveConfirmState.settle(false)}>
					{request.cancelLabel}
				</AlertDialog.Cancel>
				<AlertDialog.Action
					variant={request.destructive ? 'destructive' : 'default'}
					onclick={() => adaptiveConfirmState.settle(true)}
				>
					{request.confirmLabel}
				</AlertDialog.Action>
			</AlertDialog.Footer>
		</AlertDialog.Content>
	{/if}
</AlertDialog.Root>
