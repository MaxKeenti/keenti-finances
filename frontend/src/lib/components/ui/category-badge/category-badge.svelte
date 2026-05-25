<script lang="ts">
  import { getIsDark } from '$lib/theme.svelte';

  interface Props {
    hue: number | null;
    name: string;
    direction?: string;
    forceTheme?: 'light' | 'dark';
  }

  let { hue, name, direction, forceTheme }: Props = $props();

  const style = $derived.by(() => {
    if (hue === null || hue === undefined) return '';
    const dark = forceTheme ? forceTheme === 'dark' : getIsDark();
    const l = dark ? 0.3 : 0.92;
    const c = dark ? 0.08 : 0.05;
    const textColor = dark ? 'oklch(0.9 0 0)' : 'oklch(0.2 0 0)';
    return `background:oklch(${l} ${c} ${hue});color:${textColor};`;
  });
</script>

{#if hue !== null && hue !== undefined}
  <span
    class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
    style={style}
  >
    {name}
  </span>
{:else}
  <span class="inline-flex items-center rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium text-muted-foreground">
    {name}
  </span>
{/if}
