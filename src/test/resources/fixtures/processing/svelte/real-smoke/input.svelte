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
  const unsafe = cn(`text-center ${activeClass} p-4 flex bg-red-500 font-bold`)
</script>

<button
  {...rest}
  class={active ? "text-center p-4 flex bg-red-500 font-bold" : "font-bold flex p-4"}
  class:active={active}
>
  Save
</button>

<Button class={[
  "text-center p-4 flex bg-red-500 font-bold",
  active && "font-bold flex p-4",
  { "tracking-wide text-xs px-2 py-0.5": compact },
  className,
]}>
  Save
</Button>

<div class={`text-center p-4 flex bg-red-500 font-bold`}></div>

<style>
  .button {
    @apply text-center p-4 flex bg-red-500 font-bold;
  }
</style>
