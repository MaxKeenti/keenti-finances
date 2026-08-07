<script lang="ts">
	import { CategoryBadge } from '$lib/components/categories';
	import { Input } from '$lib/components/ui/input';
	import * as Slider from '$lib/components/ui/slider';
	import { m } from '$lib/paraglide/messages.js';
	import { hexToOklchHue, oklchHueToRepresentativeHex } from '$lib/utils/color';

	interface Props {
		hue: number;
		name: string;
		direction?: string;
		onchange: (hue: number) => void;
	}

	let { hue, name, direction, onchange }: Props = $props();
	let hexInput = $state('');

	$effect(() => {
		hexInput = oklchHueToRepresentativeHex(hue);
	});

	function onHexBlur() {
		const next = hexToOklchHue(hexInput);
		if (next !== null) onchange(next);
		else hexInput = oklchHueToRepresentativeHex(hue);
	}

	function onHexKeydown(event: KeyboardEvent) {
		if (event.key !== 'Enter') return;
		event.preventDefault();
		(event.target as HTMLInputElement).blur();
	}
</script>

<div class="flex flex-col gap-3">
	<div class="flex items-center gap-3">
		<Slider.Root
			type="single"
			min={0}
			max={359}
			step={1}
			value={hue}
			onValueChange={onchange}
			class="hue-slider flex-1"
			thumbLabel={m.common_hue()}
		/>
		<Input
			type="text"
			bind:value={hexInput}
			onblur={onHexBlur}
			onkeydown={onHexKeydown}
			class="h-9 w-24 font-mono uppercase"
			maxlength={7}
			aria-label={m.common_hex_colour()}
		/>
	</div>

	<div class="flex items-center gap-2 text-xs text-muted-foreground">
		<span>{m.common_preview()}:</span>
		<div class="rounded-md bg-background p-1.5">
			<CategoryBadge {hue} name={name || m.common_sample()} {direction} forceTheme="light" />
		</div>
		<div class="rounded-md bg-foreground p-1.5">
			<CategoryBadge {hue} name={name || m.common_sample()} {direction} forceTheme="dark" />
		</div>
	</div>
</div>
