<template>
  <section class="flex bg-red-500 p-4 text-center font-bold">
    <button v-bind:class="'flex bg-red-500 p-4 text-center font-bold'"></button>
    <div :class="[
      'flex bg-red-500 p-4 text-center font-bold',
      { 'flex p-4 font-bold': isActive },
      isCompact ? ['flex bg-red-500 p-4 text-center font-bold'] : [],
    ]"></div>
  </section>
</template>
