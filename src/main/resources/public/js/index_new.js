$("main").addClass("pre-enter").removeClass("with-hover");
setTimeout(function(){
	$("main").addClass("on-enter");
}, 500);
setTimeout(function(){	
	$("main").removeClass("pre-enter on-enter");
	setTimeout(function(){
		$("main").addClass("with-hover");
		}, 50);
	}, 2000);

	$(".flip, .back a").click(function(){
		$(".player").toggleClass("playlist");
	});

	$(".bottom a").not(".flip").click(function(){
		$(this).toggleClass("active");
	});

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
var FETCH_PATH = 'api/player'
var random = false;
var index = 0;
var sortIndex = 1;
var songList = [];
var initial = true;

function playTrack(i=-1){
    if(i!=-1){
        index = i;
	}
	$("#frontCover").attr("src",`api/fetchArt?path=${songList[index].path}`)
	$("title").html(songList[index].artist+' - '+songList[index].title);
	$("#titleView").html(songList[index].title);
	$("#infoView").html(`${songList[index].artist} - ${songList[index].album}`);
	$("#lenTime").html(timeformat(songList[index].length));
    player.src = mediaPath+songList[index].path;
    player.play()!==null ? player.play().catch(function(err){
        if(err.name=="NotSupportedError"){
            player.src = '/api/transcode?path='+songList[index].path;
			player.play();
        }
	}) : false;
}
function nextSong(){
    if(index+1>=0 && index+1<songList.length){
        index++;
	}
	else{
		index = 0;
	}
	playTrack();
}
function previousSong(){
    if(index>=0 && index-1<songList.length){
        index--;
	}
	else{
		index = songList.length-1;
	}
	playTrack();
}
function playPause(){
    if(player.paused){
        player.play();
    }
    else{
        player.pause();
    }
}
loadtracks = function loadtracks(i){
    $.getJSON(FETCH_PATH,{'s':i,'sortBy':sortIndex},function(res){
        songList = res.songs;
        $("#idxcount").children().css("font-weight","");
        $(`#range_${i}`).css("font-weight","Bold");
        $("#plList").empty();
        for(let i=0;i<res.songs.length;i++){
            $("#plList").append(`
			<li>
			<a onclick="playTrack(i=${i})">
			  <img src='api/fetchArt?path=${res.songs[i].path}'>
			  <div>
				<h3>${res.songs[i].title}</h3>
				<h4>${res.songs[i].artist} - ${res.songs[i].album}</h4>
			  </div>
			</a>
		  </li>
            `);
		};
		let html = ""
		for(let i=0;i<res.offset_count;i++){
			html+=`<span style="color:black;" onclick="loadtracks(${i})" id="range_${i}">${i}</span>&nbsp&nbsp`;
		}
		$("#idxcount").html(html);
		if(initial){
			initial = false;
			playTrack();
		}
    });    
}
player.onpause = function(){
    $("#btnPlay").find("svg").find("path").attr("d","M11,10 L18,13.74 18,22.28 11,26 M18,13.74 L26,18 26,18 18,22.28");
}
player.onplay = function(){
    $("#btnPlay").find("svg").find("path").attr("d","M11,10 L17,10 17,26 11,26 M20,10 L26,10 26,26 20,26");
}
player.onended = function(){
    nextSong();
}
player.ontimeupdate = function(){
	$("#currTime").html(timeformat(player.currentTime));
	let len = player.duration;
	let curr = player.currentTime;
	let wid = (320*curr)/len;
	$("#barLen").css({'width':wid});
}
$(document).ready(function ($) {
	$("#bgImage").css({'background-image':`url('img/pixels${Math.floor(Math.random()*12)+1}.gif')`})
    $("#btnPlay").click(function(){
        playPause();
    });
    $('#btnPrevious').click(function(){
        previousSong();
    });
    $('#btnNext').click(function(){
        nextSong();
    });
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
				<a onclick="playTrack(i=${i})">
				  <img src='api/fetchArt?path=${res.songs[i].path}'>
				  <div>
					<h3>${res.songs[i].title}</h3>
					<h4>${res.songs[i].artist} - ${res.songs[i].album}</h4>
				  </div>
				</a>
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
	$("#btnRepeat").click(function(){
		player.repeat==true ? player.repeat = false : player.repeat = true;
	});
	$("#btnRandom").click(function(){
		if(random){
			FETCH_PATH = 'api/player';
			random = false;
		}
		else{
			FETCH_PATH = 'api/radio';
			random = true;
		}
	});
	sortBy = function sortBy(){
        let sort = $("#SortSelect").val();
        sortIndex = sort;
        loadtracks();
    }
	loadtracks();
});