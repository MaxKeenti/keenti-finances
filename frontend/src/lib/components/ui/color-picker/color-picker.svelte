<script lang="ts">
  import { Input } from '$lib/components/ui/input';
  import { CategoryBadge } from '$lib/components/ui/category-badge';
  import { hexToOklchHue, oklchHueToRepresentativeHex } from '$lib/utils/color';

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
  let hexInput = $state(oklchHueToRepresentativeHex(hue));

  $effect(() => {
    hexInput = oklchHueToRepresentativeHex(hue);
  });

  function onSlider(e: Event) {
    const next = Number((e.target as HTMLInputElement).value);
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
    <input
      type="range"
      min="0"
      max="359"
      step="1"
      value={hue}
      oninput={onSlider}
      class="h-3 flex-1 cursor-pointer appearance-none rounded-full outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:h-5 [&::-webkit-slider-thumb]:w-5 [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:border-2 [&::-webkit-slider-thumb]:border-foreground [&::-webkit-slider-thumb]:bg-background [&::-webkit-slider-thumb]:cursor-grab [&::-webkit-slider-thumb]:active:cursor-grabbing [&::-moz-range-thumb]:h-5 [&::-moz-range-thumb]:w-5 [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:border-2 [&::-moz-range-thumb]:border-foreground [&::-moz-range-thumb]:bg-background"
      style="background:{sliderBg};"
      aria-label="Hue"
    />
    <Input
      type="text"
      bind:value={hexInput}
      onblur={onHexBlur}
      onkeydown={onHexKeydown}
      class="h-9 w-24 font-mono uppercase"
      maxlength={7}
      aria-label="Hex color"
    />
  </div>

  <div class="flex items-center gap-2 text-xs text-muted-foreground">
    <span>Preview:</span>
    <div class="rounded-md bg-background p-1.5">
      <CategoryBadge {hue} name={name || 'Sample'} {direction} forceTheme="light" />
    </div>
    <div class="rounded-md bg-foreground p-1.5">
      <CategoryBadge {hue} name={name || 'Sample'} {direction} forceTheme="dark" />
    </div>
  </div>
</div>
