<script lang="ts">
	/**
	 * The empty-but-healthy dashboard.
	 *
	 * Shown only when the position section *loaded* and says tracking is not set
	 * up yet. It is not a failure state and never stands in for one: an
	 * unavailable section says so with a Retry, and a User whose totals really
	 * are zero still sees their zeros rather than this.
	 */
	import * as Card from '$lib/components/ui/card';
	import { Button } from '$lib/components/ui/button';
	import { m } from '$lib/paraglide/messages.js';
</script>

<Card.Root>
	<Card.Header class="gap-1">
		<Card.Title class="font-heading text-xl">{m.dashboard_setup_title()}</Card.Title>
		<Card.Description>{m.dashboard_setup_description()}</Card.Description>
	</Card.Header>
	<Card.Content class="flex flex-wrap gap-2">
		<Button href="/accounts" size="sm">{m.dashboard_setup_action()}</Button>
		<Button href="/transactions" size="sm" variant="outline">{m.dashboard_setup_record()}</Button>
		<Button href="/boxes" size="sm" variant="outline">{m.boxes_create_first()}</Button>
	</Card.Content>
</Card.Root>
