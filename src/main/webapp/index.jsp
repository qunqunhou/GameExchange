<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>游戏虚拟经济交易所</title>
</head>
<body>

<h1>游戏虚拟经济交易所</h1>

<hr>

<p>
    <a href="vue/login.html">玩家登录</a>
</p>

<p>
    <a href="vue/market.html">交易市场</a>
</p>

<%
    Object player = session.getAttribute("player");
    if (player != null) {
        response.sendRedirect("vue/market.html");
    } else {
        response.sendRedirect("vue/login.html");
    }
%>
</body>
</html>