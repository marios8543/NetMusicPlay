const player = document.getElementById("mainAudio");
const ws = new WebSocket('wss://tzatzikiweeb.moe/Chat')
const app = new Vue({
    'el': '#mainWrap',
    data: {
        upcoming: [],
        nowPlaying: {},
        messages:[{author:"",content:"Welcome to tzatzikiweeb radio"}],
        nowListening:0,
        chatInput:"",
        times: {
            currentTime:0,
            duration:0
        },
        muted:false,
        firstPlay:false,

        songList: [],
        offset_count: 0,
        current_offset: 0,
        sortBy: 1,

        skipVotes:"",
        showSkipVotes:false
    },
    methods: {
        timeFormat (time) {
            var hrs = ~~(time / 3600);
            var mins = ~~((time % 3600) / 60);
            var secs = ~~time % 60;
            var ret = "";
            if (hrs > 0) {
                ret += "" + hrs + ":" + (mins < 10 ? "0" : "");
            }
            ret += "" + mins + ":" + (secs < 10 ? "0" : "");
            ret += "" + secs;
            return ret;
        },
        startPlayback (time = null) {
            this.showSkipVotes = false;
            this.skipVotes = "";

            player.src = "/Music/" + this.nowPlaying.path;
            this.times.duration = this.nowPlaying.length;
            player.play();
            if (time != null) player.currentTime = time;
        },
        fetchSongs (firstTime = false, noPlayback = false) {
            let _this = this
            $.getJSON("api/radio", {}, function (data) {
                app.nowPlaying = data.song;
                app.upcoming = data.upcoming;
                if (noPlayback) return;
                if (firstTime) _this.startPlayback(data.time);
                else _this.startPlayback();
            });
        },
        sendMessage () {
            if (this.chatInput.length>0) {
                ws.send(this.chatInput);
                this.chatInput = "";
            }
        },
        mute () {
            if (player.muted) player.muted = false;
            else player.muted = true;
            this.muted = player.muted;
        },
        updateVolume (event) {
            player.volume = event.target.value/100;
        },
        loadRange (i = app.current_offset) {
            $.getJSON("api/player", { 's': i, 'sortBy': app.sortBy }, function (res) {
                app.songList = res.songs;
                app.offset_count = res.offset_count;
                app.current_offset = i;
            });
        },
        selectSort (sort) {
            app.sortBy = sort;
            app.current_offset = 0;
            this.loadRange()
        },
        submitRequest (id) {
            let _this = this;
            $.getJSON("api/radio/request", {id:id}, function (res) {
                alert(`Your song is ${res.queue_position} on the list!`);
                _this.fetchSongs(noPlayback = true);
            }).fail(function (xhr, status, error) {
                alert(xhr.responseText);
            });
        },
        voteSkip () {
            let _this = this;
            $.getJSON("api/radio/voteSkip", function (res) {
                _this.showSkipVotes = true;
            }).fail(function (xhr, status, error) {
                alert(xhr.responseText);
            });
        }
    },
    mounted: function () {
        this.$nextTick(function () {
            $("#openPopup").click();
        });

        $.getJSON("api/player", { 's': 0, 'sortBy': 1 }, function (res) {
            app.songList = res.songs;
            app.offset_count = res.offset_count;
        });
    }
});

ws.onmessage = function(event) {
    packet = JSON.parse(event.data);
    if(packet.author=="force_reload") {
        setTimeout(function() {
            app.fetchSongs();
        },3000);
        return;
    }
    if(packet.author=="skip_votes") {
        app.skipVotes = packet.content;
        return;
    }
    app.nowListening = packet.nowListening;
    delete packet.nowListening;
    app.messages.push(packet);
    if (app.messages.length > 50) app.messages.shift();
}

player.onended = function () {
    setTimeout(function() {
        app.fetchSongs();
    },3000)
}

player.ontimeupdate = function () {
    app.times.currentTime = player.currentTime;
}