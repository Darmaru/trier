<script>
  let {
    class: className = "",
    compact = false,
    active = false,
    ...rest
  } = $props()

  const classes = cn([
    "flex bg-red-500 p-4 text-center font-bold",
    active && "flex p-4 font-bold",
    { "px-2 py-0.5 text-xs tracking-wide": compact },
    className,
  ])
  const unsafe = cn(`text-center ${activeClass} p-4 flex bg-red-500 font-bold`)
</script>

<button
  {...rest}
  class={active ? "flex bg-red-500 p-4 text-center font-bold" : "flex p-4 font-bold"}
  class:active={active}
>
  Save
</button>

<Button class={[
  "flex bg-red-500 p-4 text-center font-bold",
  active && "flex p-4 font-bold",
  { "px-2 py-0.5 text-xs tracking-wide": compact },
  className,
]}>
  Save
</Button>

<div class={`flex bg-red-500 p-4 text-center font-bold`}></div>

<style>
  .button {
    @apply flex bg-red-500 p-4 text-center font-bold;
  }
</style>
