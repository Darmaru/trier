<script>
  let {
    class: className = "",
    compact = false,
    active = false,
    ...rest
  } = $props()

  const classes = cn([
    "text-center p-4 flex bg-red-500 font-bold",
    active && "font-bold flex p-4",
    { "tracking-wide text-xs px-2 py-0.5": compact },
    className,
  ])
</script>

<button {...rest} class={classes}>Save</button>
