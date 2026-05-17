<script lang="ts">
  import { getIsDark } from '$lib/theme.svelte';

  interface Props {
    direction: 'INGRESS' | 'EGRESS' | 'BOTH';
    value: string | null;
    onchange: (hue: string) => void;
  }

  let { direction, value, onchange }: Props = $props();

  const HUES: Record<string, number[]> = {
    INGRESS: [100, 120, 140, 150, 160, 170],
    EGRESS:  [10,  20,  30,  40, 350,   0],
    BOTH:    [220, 240, 260, 270, 280, 300],
  };

  const hues = $derived(HUES[direction] ?? HUES['BOTH']);

  function swatchStyle(hue: number): string {
    const dark = getIsDark();
    const l = dark ? 0.55 : 0.75;
    const c = dark ? 0.14 : 0.18;
    return `background:oklch(${l} ${c} ${hue});`;
  }
</script>

<div class="flex flex-wrap gap-2">
  {#each hues as hue (hue)}
    {@const selected = value === String(hue)}
    <button
      type="button"
      onclick={() => onchange(String(hue))}
      class="h-7 w-7 rounded-full border-2 transition-transform hover:scale-110 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      class:border-foreground={selected}
      class:border-transparent={!selected}
      class:ring-2={selected}
      class:ring-offset-2={selected}
      style={swatchStyle(hue)}
      title={`Hue ${hue}`}
      aria-label={`Select hue ${hue}`}
      aria-pressed={selected}
    ></button>
  {/each}
  {#if value}
    <button
      type="button"
      onclick={() => onchange('')}
      class="h-7 px-2 rounded-full border border-border text-xs text-muted-foreground hover:bg-muted"
    >
      Clear
    </button>
  {/if}
</div>
