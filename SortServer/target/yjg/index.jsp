<html>
<head>
    <meta http-equiv="CONTENT-TYPE" content="text/html; charset=utf-8">
    <script>
        ajax = new XMLHttpRequest();
        ajax.onreadystatechange=function () {
            alert(ajax.status)
            if(ajax.readyState == 4&& ajax.status == 200){
                document.getElementById("h").innerHTML=ajax.responseText
            }
        }
        function run() {
            ajax.open("POST","/sortor/nextdata",true)
            ajax.send("[1,2,3,5,9,8,2,6,7,4,1,8,5]")
        }
    </script>
</head>
<body>
<h2>Hello World!</h2>
<input type="button" value="提交" onclick="run()">
<div id = "h">

</div>
</body>
</html>
