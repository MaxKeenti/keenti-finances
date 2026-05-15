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
  import type { PageData } from "./$types";

  const categorySchema = z.object({
    id: z.coerce.number().optional(),
    name: z.string().min(1, "Name is required"),
    type: z.enum(["INGRESS", "EGRESS", "BOTH"]),
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
        toast.success(editMode ? "Category updated." : "Category created.");
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
    sf.reset({ data: { name: "", type: "INGRESS" } });
    dialogOpen = true;
  }

  function openEdit(cat: { id: number; name: string; type: string }) {
    editMode = true;
    form.set({
      id: cat.id,
      name: cat.name,
      type: cat.type as "INGRESS" | "EGRESS" | "BOTH",
    });
    dialogOpen = true;
  }

  function openDelete(cat: { id: number; name: string }) {
    deleteTargetId = cat.id;
    deleteTargetName = cat.name;
    deleteDialogOpen = true;
  }

  const typeLabel: Record<string, string> = {
    INGRESS: "Ingress",
    EGRESS: "Egress",
    BOTH: "Both",
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
      <h1 class="text-2xl font-semibold tracking-tight">Categories</h1>
      <p class="text-sm text-muted-foreground">
        Manage your income and expense categories.
      </p>
    </div>
    <Button onclick={openCreate}>New Category</Button>
  </div>

  {#if data.categories.length === 0}
    <Empty.Root class="border">
      <Empty.Title>No categories yet.</Empty.Title>
      <Empty.Description>Create one to get started.</Empty.Description>
    </Empty.Root>
  {:else}
    <div class="rounded-lg border">
      <Table.Root>
        <Table.Header>
          <Table.Row>
            <Table.Head>Name</Table.Head>
            <Table.Head>Type</Table.Head>
            <Table.Head class="w-[120px] text-right">Actions</Table.Head>
          </Table.Row>
        </Table.Header>
        <Table.Body>
          {#each data.categories as cat (cat.id)}
            <Table.Row>
              <Table.Cell class="font-medium">{cat.name}</Table.Cell>
              <Table.Cell>
                <Badge variant={typeBadgeVariant[cat.type]}>
                  {typeLabel[cat.type] ?? cat.type}
                </Badge>
              </Table.Cell>
              <Table.Cell class="text-right">
                <div class="flex justify-end gap-2">
                  <Button variant="outline" size="sm" onclick={() => openEdit(cat)}>Edit</Button>
                  <Button variant="destructive" size="sm" onclick={() => openDelete(cat)}>Delete</Button>
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
      <Dialog.Title>{editMode ? "Edit Category" : "New Category"}</Dialog.Title>
      <Dialog.Description>
        {editMode
          ? "Update the category details below."
          : "Fill in the details for the new category."}
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
            <Form.Label>Name</Form.Label>
            <Input {...props} bind:value={$form.name} placeholder="e.g. Salary" />
          {/snippet}
        </Form.Control>
        <Form.FieldErrors />
      </Form.Field>

      <Form.Field form={sf} name="type">
        <Form.Control>
          {#snippet children({ props })}
            {@const { name: fieldName, ...triggerProps } = props}
            <Form.Label>Type</Form.Label>
            <NativeSelect
              name={fieldName}
              value={$form.type}
              onValueChange={(v) => { $form.type = v as 'INGRESS' | 'EGRESS' | 'BOTH'; }}
              placeholder="Select type…"
              items={[
                { value: 'INGRESS', label: 'Ingress (income)' },
                { value: 'EGRESS', label: 'Egress (expense)' },
                { value: 'BOTH', label: 'Both' },
              ]}
              {...triggerProps}
            />
          {/snippet}
        </Form.Control>
        <Form.FieldErrors />
      </Form.Field>

      <Dialog.Footer>
        <Button type="button" variant="outline" onclick={() => (dialogOpen = false)}>Cancel</Button>
        <Button type="submit" disabled={$submitting}>
          {$submitting ? "Saving…" : editMode ? "Update" : "Create"}
        </Button>
      </Dialog.Footer>
    </form>
  </Dialog.Content>
</Dialog.Root>

<!-- Delete confirmation dialog -->
<Dialog.Root bind:open={deleteDialogOpen}>
  <Dialog.Content class="sm:max-w-md">
    <Dialog.Header>
      <Dialog.Title>Delete Category</Dialog.Title>
      <Dialog.Description>
        Are you sure you want to delete <strong>{deleteTargetName}</strong>?
        This action cannot be undone.
      </Dialog.Description>
    </Dialog.Header>
    <form
      method="POST"
      action="?/delete"
      use:kitEnhance={async () => {
        return async ({ result, update }) => {
          if (result.type === "success") {
            deleteDialogOpen = false;
            toast.success("Category deleted.");
            await update();
          } else {
            const msg =
              (result as { data?: { message?: string } }).data?.message ??
              "Failed to delete category.";
            toast.error(msg);
          }
        };
      }}
    >
      <input type="hidden" name="id" value={deleteTargetId} />
      <Dialog.Footer>
        <Button type="button" variant="outline" onclick={() => (deleteDialogOpen = false)}>
          Cancel
        </Button>
        <Button type="submit" variant="destructive">Delete</Button>
      </Dialog.Footer>
    </form>
  </Dialog.Content>
</Dialog.Root>
