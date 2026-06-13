<div {{ $attributes->class([
    'text-center p-4 flex bg-red-500 font-bold' => $active,
    'font-bold flex p-4',
]) }}></div>

<div {{ $attributes->merge([
    'class' => 'text-center p-4 flex bg-red-500 font-bold',
]) }}></div>
