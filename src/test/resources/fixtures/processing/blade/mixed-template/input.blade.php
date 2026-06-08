<?php
$classes = [
    'text-center p-4 flex bg-red-500 font-bold',
];
?>

<x-card
  class="text-center p-4 flex bg-red-500 font-bold"
  data-state="{{ $state }}"
  :class="$active ? 'font-bold flex p-4' : ''"
>
  <div @class([
    'tracking-wide text-xs px-2 py-0.5' => $compact,
    'font-bold flex p-4',
  ])>
    {{ $slot }}
  </div>
</x-card>
