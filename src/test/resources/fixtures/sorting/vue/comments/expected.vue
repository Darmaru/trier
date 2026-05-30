<template>
  <div :class="[
    // 'text-center p-4 flex bg-red-500 font-bold'
    'flex p-4 font-bold',
    /*
     * `text-center p-4 flex bg-red-500 font-bold`
     */
    isActive && 'flex bg-red-500 p-4 text-center font-bold',
  ]"></div>
</template>
