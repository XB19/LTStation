var waveHeight = 0;
var waterHeight = 0;
var waveNum = 61;
var waterwave = 10;
function waves() {
    if (waveHeight < waveNum) {
        $(".wave").css("height", waveHeight + "%");
        waveHeight++;
    }

    if (waterHeight < waterwave) {
        $(".wave2").css("height", waveHeight + "%");
        waterHeight++;
    }
    setTimeout(function () { waves() }, 15);
}

$(function () {
    $("#denfenjs").html(waveNum + "%");
    waves();
})