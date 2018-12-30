var b = document.documentElement;
b.setAttribute('data-useragent', navigator.userAgent);
b.setAttribute('data-platform', navigator.platform);

function timeformat(time){   
    var hrs = ~~(time / 3600);
    var mins = ~~((time % 3600) / 60);
    var secs = ~~time % 60;
    var ret = "";
    if(hrs > 0){
        ret+=""+ hrs+":"+(mins<10?"0":"");
    }
    ret+=""+mins+":"+(secs<10?"0":"");
    ret+=""+secs;
    return ret;
}

var player = document.getElementById("audio1");
var mediaPath = '/api/fetchSong?path='
var flacPath = '/api/transcodeFlac?path='
var index = 0;
var sortIndex = 1;
var songList = [];

function playTrack(i=-1){
    if(i!=-1){
        index = i;
    }
    $("#npTitle").html(songList[index].title);
    $("#HidnpTitle").html(songList[index].title);
    $("#npAction").html(`${songList[index].artist} (${songList[index].album})`);
    $("title").html(songList[index].artist+' - '+songList[index].title);
    if(songList[index].path.endsWith("flac")){
        player.src = flacPath+songList[index].path;  
    }
    else{
        player.src = mediaPath+songList[index].path;
    }
    player.play()!==null ? player.play().catch(function(err){
        if(err.name=="NotSupportedError"){
            player.src = flacPath+songList[index].path;
            player.play();
        }
    }) : false;
    $("li").removeClass("plSel");
    $('span[id="playsym"]').css({'display':'none'});
    $('span[id="indexenum"]').css({'display':'inline'});
    $("li").eq(index).addClass("plSel");
    $("li").eq(index).find("#playsym").css({'display':'inline'});
    $("li").eq(index).find("#indexenum").css({'display':'none'});
}
function nextSong(){
    if(index+1>=0 && index+1<=songList.length){
        index++;
        playTrack();
    }
}
function previousSong(){
    if(index>=0 && index-1<=songList.length){
        index--;
        playTrack();
    }
}

function playPause(){
    if(player.paused){
        player.play();
    }
    else{
        player.pause();
    }
}

function loadrange(i){
    $.getJSON("api/player",{'s':i,'sortBy':sortIndex},function(res){
        songList = res.songs;
        $("#idxcount").children().css("font-weight","");
        $(`#range_${i}`).css("font-weight","Bold");
        $("#plList").empty();
        for(let i=0;i<res.songs.length;i++){
            $("#plList").append(`
            <li>
            <div class="plItem" onclick="playTrack(i=${i})">
                <div class="plNum"><span style="display:inline;" id="indexenum">${i+1}.</span><span id="playsym" style="display:none;">▶️</span></div>
                <div class="plTitle">${res.songs[i].artist} - ${res.songs[i].title} (${res.songs[i].album})</div>
                <div class="plLength">${timeformat(res.songs[i].length)}</div>
            </div>
            </li>
            `);
        };
    });    
}
player.onpause = function(){
    $("#btnPlay").html("▶️");
    $("#HidbtnPlay").html("▶️");
}
player.onplay = function(){
    $("#btnPlay").html("⏸️");
    $("#HidbtnPlay").html("⏸️");
}
player.onended = function(){
    nextSong();
}
$(document).ready(function ($) {
    function loadtracks(){
        $.getJSON("api/player",{'sortBy':sortIndex},function(res){
            $("#plList").empty();
            songList = res.songs;
            for(let i=0;i<res.songs.length;i++){
                $("#plList").append(`
                <li>
                <div class="plItem" onclick="playTrack(i=${i})">
                    <div class="plNum"><span style="display:inline;" id="indexenum">${i+1}.</span><span id="playsym" style="display:none;">▶️</span></div>
                    <div class="plTitle">${res.songs[i].artist} - ${res.songs[i].title} (${res.songs[i].album})</div>
                    <div class="plLength">${timeformat(res.songs[i].length)}</div>
                </div>
                </li>
                `);
            };
            let html = ""
            for(let i=0;i<res.offset_count;i++){
                html+=`<span style="color:#ffffff;" onclick="loadrange(${i})" id="range_${i}">${i}</span>&nbsp&nbsp`;
            }
            $("#idxcount").html(html);
        });
    }
    loadtracks();
    nextSong();
    $("#btnPlay").click(function(){
        playPause();
    });
    $('#btnPrev').click(function(){
        previousSong();
    });
    $("HidbtnPrev").click(function(){
        previousSong();
    });
    $('#btnNext').click(function(){
        nextSong();
    });
    $("#HidbtnNext").click(function(){
        nextSong();
    });
    $("#HidbtnPlay").click(function(){
        playPause();
    })
    $("#btnRescan").click(function(){
        $.getJSON("/api/rescanLibrary",function(res){
            let newDiv = $('#notifs').css({position: 'absolute', left: '100px', top: '100px','color':'#ffffff'}).text(`Rescanned Library. ${res.added} songs added (${res.song_count} total)`).appendTo($('body'));
            newDiv.fadeOut(5000);
        });
    });
    $("#btnSearch").click(function(){
        let query = $("#searchinput").val();
        if(query==""){
            $("#btnBack").click();
        }
        $.getJSON("/api/search",{'q':query},function(res){
            songList = res.songs;
            $("#plList").empty();
            for(let i=0;i<res.songs.length;i++){
                $("#plList").append(`
                <li>
                <div class="plItem" onclick="playTrack(i=${i})">
                    <div class="plNum"><span style="display:inline;" id="indexenum">${i+1}.</span><span id="playsym" style="display:none;">▶️</span></div>
                    <div class="plTitle">${res.songs[i].artist} - ${res.songs[i].title} (${res.songs[i].album})</div>
                    <div class="plLength">${timeformat(res.songs[i].length)}</div>
                </div>
                </li>
                `);
            };   
        }); 
    });
    $(document).keypress(function(e){
        if (e.which == 13){
            $("#btnSearch").click();
        }
    });
    $("#btnBack").click(function(){
        loadtracks();
    });

    sortBy = function sortBy(){
        let sort = $("#SortSelect").val();
        sortIndex = sort;
        loadtracks();
    }

    $(window).scroll(function(){
        if(!$("#audiowrap").visible()){
            $("#hidControl").css({'display':'inline'});
        }
        else{
            $("#hidControl").css({'display':'none'});
        }
    });

});