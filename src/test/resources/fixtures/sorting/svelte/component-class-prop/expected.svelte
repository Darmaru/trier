<script>
  let props = $props()
</script>

<Button {...props} class={["flex bg-red-500 p-4 text-center font-bold", props.class]}>
  Save
</Button>
