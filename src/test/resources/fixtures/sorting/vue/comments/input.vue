<template>
  <div :class="[
    // 'text-center p-4 flex bg-red-500 font-bold'
    'font-bold flex p-4',
    /*
     * `text-center p-4 flex bg-red-500 font-bold`
     */
    isActive && 'text-center p-4 flex bg-red-500 font-bold',
  ]"></div>
</template>
