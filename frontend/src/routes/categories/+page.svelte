<script lang="ts">
  import { superForm } from "sveltekit-superforms";
  import { zod4Client } from "sveltekit-superforms/adapters";
  import { z } from "zod";
  import { toast } from "svelte-sonner";
  import { enhance as kitEnhance } from "$app/forms";
  import * as Table from "$lib/components/ui/table";
  import * as Dialog from "$lib/components/ui/dialog";
  import * as Form from "$lib/components/ui/form";
  import * as Alert from "$lib/components/ui/alert";
  import { NativeSelect } from "$lib/components/native-select";
  import * as Empty from "$lib/components/ui/empty";
  import { Input } from "$lib/components/ui/input";
  import { Button } from "$lib/components/ui/button";
  import { Badge } from "$lib/components/ui/badge";
  import { CategoryBadge } from "$lib/components/ui/category-badge";
  import { ColorPicker } from "$lib/components/ui/color-picker";
  import { m } from "$lib/paraglide/messages.js";
  import type { PageData } from "./$types";

  const DIRECTION_DEFAULT_HUE = { INGRESS: 100, EGRESS: 10, BOTH: 220 } as const;

  const categorySchema = z.object({
    id: z.coerce.number().optional(),
    name: z.string().min(1, m.validation_name_required()),
    type: z.enum(["INGRESS", "EGRESS", "BOTH"]),
    hue: z.coerce.number().int().min(0).max(359),
  });

  let { data }: { data: PageData } = $props();

  let dialogOpen = $state(false);
  let deleteDialogOpen = $state(false);
  let editMode = $state(false);
  let deleteTargetId = $state<number | null>(null);
  let deleteTargetName = $state("");

  const sf = superForm(data.form, {
    validators: zod4Client(categorySchema),
    onResult({ result }) {
      if (result.type === "success") {
        dialogOpen = false;
        toast.success(editMode ? m.categories_updated() : m.categories_created());
      } else if (result.type === "failure") {
        const msg = (result.data as Record<string, unknown> | undefined)
          ?.form as { message?: string } | undefined;
        if (msg?.message) toast.error(msg.message);
      }
    },
  });
  const { form, enhance, submitting, message } = sf;

  function openCreate() {
    editMode = false;
    sf.reset({
      data: { name: "", type: "INGRESS", hue: DIRECTION_DEFAULT_HUE.INGRESS },
    });
    dialogOpen = true;
  }

  function openEdit(cat: { id: number; name: string; type: string; hue: number }) {
    editMode = true;
    form.set({
      id: cat.id,
      name: cat.name,
      type: cat.type as "INGRESS" | "EGRESS" | "BOTH",
      hue: cat.hue,
    });
    dialogOpen = true;
  }

  function openDelete(cat: { id: number; name: string }) {
    deleteTargetId = cat.id;
    deleteTargetName = cat.name;
    deleteDialogOpen = true;
  }

  const typeLabel: Record<string, () => string> = {
    INGRESS: m.direction_ingress,
    EGRESS: m.direction_egress,
    BOTH: m.direction_both,
  };

  const typeBadgeVariant: Record<string, 'success' | 'destructive' | 'info'> = {
    INGRESS: 'success',
    EGRESS: 'destructive',
    BOTH: 'info',
  };
</script>

<div class="space-y-6">
  <div class="flex items-center justify-between">
    <div>
      <h1 class="text-2xl font-semibold tracking-tight">{m.categories_title()}</h1>
      <p class="text-sm text-muted-foreground">
        {m.categories_description()}
      </p>
    </div>
    <Button onclick={openCreate}>{m.categories_new()}</Button>
  </div>

  {#if data.categories.length === 0}
    <Empty.Root class="border">
      <Empty.Title>{m.categories_empty_title()}</Empty.Title>
      <Empty.Description>{m.categories_empty_description()}</Empty.Description>
    </Empty.Root>
  {:else}
    <div class="rounded-lg border">
      <Table.Root>
        <Table.Header>
          <Table.Row>
            <Table.Head>{m.common_name()}</Table.Head>
            <Table.Head>{m.common_type()}</Table.Head>
            <Table.Head class="w-[120px] text-right">{m.common_actions()}</Table.Head>
          </Table.Row>
        </Table.Header>
        <Table.Body>
          {#each data.categories as cat (cat.id)}
            <Table.Row>
              <Table.Cell class="font-medium">
                <CategoryBadge hue={cat.hue} name={cat.name} direction={cat.type} />
              </Table.Cell>
              <Table.Cell>
                <Badge variant={typeBadgeVariant[cat.type]}>
                  {typeLabel[cat.type]?.() ?? cat.type}
                </Badge>
              </Table.Cell>
              <Table.Cell class="text-right">
                <div class="flex justify-end gap-2">
                  <Button variant="outline" size="sm" onclick={() => openEdit(cat)}>{m.common_edit()}</Button>
                  <Button variant="destructive" size="sm" onclick={() => openDelete(cat)}>{m.common_delete()}</Button>
                </div>
              </Table.Cell>
            </Table.Row>
          {/each}
        </Table.Body>
      </Table.Root>
    </div>
  {/if}
</div>

<!-- Create / Edit dialog -->
<Dialog.Root bind:open={dialogOpen}>
  <Dialog.Content class="sm:max-w-md">
    <Dialog.Header>
      <Dialog.Title>{editMode ? m.categories_edit_title() : m.categories_new_title()}</Dialog.Title>
      <Dialog.Description>
        {editMode
          ? m.categories_edit_description()
          : m.categories_new_description()}
      </Dialog.Description>
    </Dialog.Header>

    {#if $message}
      <Alert.Root variant="destructive">
        <Alert.Description>{$message}</Alert.Description>
      </Alert.Root>
    {/if}

    <form
      method="POST"
      action={editMode ? "?/update" : "?/create"}
      use:enhance
      class="grid gap-4"
    >
      {#if editMode && $form.id}
        <input type="hidden" name="id" value={$form.id} />
      {/if}

      <Form.Field form={sf} name="name">
        <Form.Control>
          {#snippet children({ props })}
            <Form.Label>{m.common_name()}</Form.Label>
            <Input {...props} bind:value={$form.name} placeholder={m.categories_placeholder_name()} />
          {/snippet}
        </Form.Control>
        <Form.FieldErrors />
      </Form.Field>

      <Form.Field form={sf} name="type">
        <Form.Control>
          {#snippet children({ props })}
            {@const { name: fieldName, ...triggerProps } = props}
            <Form.Label>{m.common_type()}</Form.Label>
            <NativeSelect
              name={fieldName}
              value={$form.type}
              onValueChange={(v) => {
                const next = v as 'INGRESS' | 'EGRESS' | 'BOTH';
                if (!editMode) $form.hue = DIRECTION_DEFAULT_HUE[next];
                $form.type = next;
              }}
              placeholder={m.common_select_type()}
              items={[
                { value: 'INGRESS', label: m.direction_ingress_income() },
                { value: 'EGRESS', label: m.direction_egress_expense() },
                { value: 'BOTH', label: m.direction_both() },
              ]}
              {...triggerProps}
            />
          {/snippet}
        </Form.Control>
        <Form.FieldErrors />
      </Form.Field>

      <div class="grid gap-1.5">
        <span class="text-sm font-medium leading-none">{m.common_colour()}</span>
        <input type="hidden" name="hue" value={$form.hue} />
        <ColorPicker
          name={$form.name}
          direction={$form.type}
          hue={$form.hue}
          onchange={(h) => { $form.hue = h; }}
        />
      </div>

      <Dialog.Footer>
        <Button type="button" variant="outline" onclick={() => (dialogOpen = false)}>{m.common_cancel()}</Button>
        <Button type="submit" disabled={$submitting}>
          {$submitting ? m.common_saving() : editMode ? m.common_update() : m.common_create()}
        </Button>
      </Dialog.Footer>
    </form>
  </Dialog.Content>
</Dialog.Root>

<!-- Delete confirmation dialog -->
<Dialog.Root bind:open={deleteDialogOpen}>
  <Dialog.Content class="sm:max-w-md">
    <Dialog.Header>
      <Dialog.Title>{m.categories_delete_title()}</Dialog.Title>
      <Dialog.Description>
        {m.delete_confirm_prefix()} <strong>{deleteTargetName}</strong>{m.delete_confirm_suffix()}
      </Dialog.Description>
    </Dialog.Header>
    <form
      method="POST"
      action="?/delete"
      use:kitEnhance={async () => {
        return async ({ result, update }) => {
          if (result.type === "success") {
            deleteDialogOpen = false;
            toast.success(m.categories_trashed());
            await update();
          } else {
            const msg =
              (result as { data?: { message?: string } }).data?.message ??
              m.categories_delete_failed();
            toast.error(msg);
          }
        };
      }}
    >
      <input type="hidden" name="id" value={deleteTargetId} />
      <Dialog.Footer>
        <Button type="button" variant="outline" onclick={() => (deleteDialogOpen = false)}>
          {m.common_cancel()}
        </Button>
        <Button type="submit" variant="destructive">{m.common_delete()}</Button>
      </Dialog.Footer>
    </form>
  </Dialog.Content>
</Dialog.Root>
