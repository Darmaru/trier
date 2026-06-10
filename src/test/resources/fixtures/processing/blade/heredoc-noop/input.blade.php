<?php
$template = <<<HTML
<div class="text-center p-4 flex bg-red-500 font-bold"></div>
HTML;
?>

<div @class(['font-bold flex p-4' => $active])></div>
