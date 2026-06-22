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
    class="text-center p-4 flex bg-red-500 font-bold"
    ::class="'text-center p-4 flex bg-red-500 font-bold'"
    :active="$active"
    :compact="$compact"
>
    Save
</x-button>

<div
    class="text-center p-4 flex bg-red-500 font-bold"
    @class([
        'text-center p-4 flex bg-red-500 font-bold' => $active,
        'font-bold flex p-4',
        $compact => 'tracking-wide text-xs px-2 py-0.5',
    ])
>
    Dashboard
</div>

<div @class([
    {{-- 'text-center p-4 flex bg-red-500 font-bold' => $ignored, --}}
    /* 'font-bold flex p-4' */
    'text-center p-4 flex bg-red-500 font-bold' => $active,
    'font-bold flex p-4',
])></div>

<div {{ $attributes->class([
    'text-center p-4 flex bg-red-500 font-bold' => $active,
    'font-bold flex p-4',
]) }}></div>
