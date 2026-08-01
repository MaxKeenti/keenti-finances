<script lang="ts">
  import { Input } from '$lib/components/ui/input';
  import * as Slider from '$lib/components/ui/slider';
  import { CategoryBadge } from '$lib/components/ui/category-badge';
  import { hexToOklchHue, oklchHueToRepresentativeHex } from '$lib/utils/color';
  import { m } from '$lib/paraglide/messages.js';

  interface Props {
    hue: number;
    name: string;
    direction?: string;
    onchange: (hue: number) => void;
  }

  let { hue, name, direction, onchange }: Props = $props();

  // Local hex input state. Mirrors the representative hex of the current hue
  // whenever it changes externally; the user can type a hex to drive the hue
  // (lossily — only the hue component survives, as the dual preview makes
  // plain).
  let hexInput = $state('');

  $effect(() => {
    hexInput = oklchHueToRepresentativeHex(hue);
  });

  function onSlider(next: number) {
    onchange(next);
  }

  function onHexBlur() {
    const next = hexToOklchHue(hexInput);
    if (next !== null) {
      onchange(next);
    } else {
      // Restore previous representative hex if the input was junk or
      // achromatic; the hue value itself is unchanged.
      hexInput = oklchHueToRepresentativeHex(hue);
    }
  }

  function onHexKeydown(e: KeyboardEvent) {
    if (e.key === 'Enter') {
      e.preventDefault();
      (e.target as HTMLInputElement).blur();
    }
  }

  // Vivid hue strip — see Q11(a). Constant L=0.7, C=0.18 across the full
  // wheel, drawn as a 12-stop linear gradient. The badge preview below shows
  // the muted theme-fixed rendering that will actually ship.
  const sliderBg =
    'linear-gradient(to right,' +
    Array.from({ length: 13 }, (_, i) => `oklch(0.7 0.18 ${i * 30})`).join(',') +
    ')';
</script>

<div class="flex flex-col gap-3">
  <div class="flex items-center gap-3">
    <Slider.Root
      type="single"
      min={0}
      max={359}
      step={1}
      value={hue}
      onValueChange={onSlider}
      class="hue-slider flex-1"
      style={`--hue-slider-bg: ${sliderBg};`}
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

<style>
  :global(.hue-slider [data-slot='slider-track']) {
    background: var(--hue-slider-bg);
  }

  :global(.hue-slider [data-slot='slider-range']) {
    background: transparent;
  }
</style>
