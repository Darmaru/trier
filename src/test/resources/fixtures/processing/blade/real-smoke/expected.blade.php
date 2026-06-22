@php
    $active = true;
    $compact = true;
    $unsafe = [
        'text-center p-4 flex bg-red-500 font-bold',
        $active ? 'font-bold flex p-4' : null,
    ];
    $template = <<<HTML
<div class="text-center p-4 flex bg-red-500 font-bold"></div>
HTML;
@endphp

{{-- <div class="text-center p-4 flex bg-red-500 font-bold"></div> --}}

@verbatim
    <div class="text-center p-4 flex bg-red-500 font-bold"></div>
    <div @class(['font-bold flex p-4' => active])></div>
@endverbatim

<x-button
    class="flex bg-red-500 p-4 text-center font-bold"
    ::class="'text-center p-4 flex bg-red-500 font-bold'"
    :active="$active"
    :compact="$compact"
>
    Save
</x-button>

<div
    class="flex bg-red-500 p-4 text-center font-bold"
    @class([
        'flex bg-red-500 p-4 text-center font-bold' => $active,
        'flex p-4 font-bold',
        $compact => 'px-2 py-0.5 text-xs tracking-wide',
    ])
>
    Dashboard
</div>

<div @class([
    {{-- 'text-center p-4 flex bg-red-500 font-bold' => $ignored, --}}
    /* 'font-bold flex p-4' */
    'flex bg-red-500 p-4 text-center font-bold' => $active,
    'flex p-4 font-bold',
])></div>

<div {{ $attributes->class([
    'text-center p-4 flex bg-red-500 font-bold' => $active,
    'font-bold flex p-4',
]) }}></div>
