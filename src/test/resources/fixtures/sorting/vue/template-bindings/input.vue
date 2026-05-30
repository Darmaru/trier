<template>
  <section class="text-center p-4 flex bg-red-500 font-bold">
    <button v-bind:class="'text-center p-4 flex bg-red-500 font-bold'"></button>
    <div :class="[
      'text-center p-4 flex bg-red-500 font-bold',
      { 'font-bold flex p-4': isActive },
      isCompact ? ['text-center p-4 flex bg-red-500 font-bold'] : [],
    ]"></div>
  </section>
</template>
