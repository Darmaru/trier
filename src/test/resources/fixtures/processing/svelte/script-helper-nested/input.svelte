<script>
  const button = cn([
    "text-center p-4 flex bg-red-500 font-bold",
    active && "font-bold flex p-4",
    { "tracking-wide text-xs px-2 py-0.5": compact },
  ])
  const variants = {
    primary: cn("font-bold flex p-4"),
  }
</script>

<button class={button}>Save</button>
