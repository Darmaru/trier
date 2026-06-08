<?php
$classes = [
    'text-center p-4 flex bg-red-500 font-bold',
];
?>

<x-card
  class="flex bg-red-500 p-4 text-center font-bold"
  data-state="{{ $state }}"
  :class="$active ? 'flex p-4 font-bold' : ''"
>
  <div @class([
    'px-2 py-0.5 text-xs tracking-wide' => $compact,
    'flex p-4 font-bold',
  ])>
    {{ $slot }}
  </div>
</x-card>
