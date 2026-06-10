<?php
$template = <<<HTML
<div class="text-center p-4 flex bg-red-500 font-bold"></div>
HTML;
?>

<div @class(['flex p-4 font-bold' => $active])></div>
