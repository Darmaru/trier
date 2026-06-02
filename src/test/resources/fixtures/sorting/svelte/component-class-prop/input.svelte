<script>
  let props = $props()
</script>

<Button {...props} class={["text-center p-4 flex bg-red-500 font-bold", props.class]}>
  Save
</Button>
